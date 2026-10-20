#!/usr/bin/env python3
"""Dependency-aware, persistent task tracker for this repository."""
from __future__ import annotations
import argparse, json, os, re, sys, time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "tasks" / "manifest.json"
STATUS_DIR = ROOT / "tracking" / "task-status"
LOG_DIR = ROOT / "tracking" / "logs"
LOCK = ROOT / "tracking" / ".project-status.lock"
STATES = {"TODO", "IN_PROGRESS", "BLOCKED", "DONE"}

def now(): return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
def read_json(path):
    with path.open(encoding="utf-8") as f: return json.load(f)
def write_json(path, value):
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(temp, path)
def manifest(): return read_json(MANIFEST)
def tasks(): return {t["id"]: t for t in manifest()["tasks"]}
def status(task_id): return read_json(STATUS_DIR / f"{task_id}.json")

@contextmanager
def locked():
    deadline = time.monotonic() + 5
    while True:
        try:
            fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.close(fd); break
        except FileExistsError:
            if time.monotonic() >= deadline: raise RuntimeError("task tracker lock timed out")
            time.sleep(.05)
    try: yield
    finally: LOCK.unlink(missing_ok=True)

def validate():
    data = manifest(); errors = []
    if data.get("schema_version") != 1 or not isinstance(data.get("tasks"), list): errors.append("invalid manifest header")
    ts = data.get("tasks", []); ids = [t.get("id") for t in ts]
    if len(ids) != len(set(ids)): errors.append("duplicate task ID")
    known = set(ids)
    for task in ts:
        tid = task.get("id", "")
        if not re.fullmatch(r"T\d{2}", tid): errors.append(f"invalid ID: {tid}")
        for dep in task.get("dependencies", []):
            if dep not in known: errors.append(f"{tid}: unknown dependency {dep}")
        criteria = [a.get("id") for a in task.get("acceptance_criteria", [])]
        if not criteria or len(criteria) != len(set(criteria)): errors.append(f"{tid}: invalid criteria")
        for required in (ROOT / "tasks" / f"{tid}.md", STATUS_DIR / f"{tid}.json", LOG_DIR / f"{tid}.md"):
            if not required.exists(): errors.append(f"missing {required.relative_to(ROOT)}")
        if (STATUS_DIR / f"{tid}.json").exists():
            s = status(tid)
            if s.get("task_id") != tid or s.get("status") not in STATES: errors.append(f"{tid}: invalid status")
            if set(s.get("acceptance", {})) != set(criteria): errors.append(f"{tid}: acceptance mismatch")
    visiting, visited = set(), set()
    graph = {t["id"]: t.get("dependencies", []) for t in ts if t.get("id")}
    def visit(node):
        if node in visiting: errors.append(f"dependency cycle at {node}"); return
        if node in visited: return
        visiting.add(node)
        for dep in graph.get(node, []): visit(dep)
        visiting.remove(node); visited.add(node)
    for node in graph: visit(node)
    if errors: raise ValueError("\n".join(errors))
    return ts

def ready(task, all_status):
    return all_status[task["id"]]["status"] == "TODO" and all(all_status[d]["status"] == "DONE" for d in task["dependencies"])
def append_log(tid, event, owner, message):
    with (LOG_DIR / f"{tid}.md").open("a", encoding="utf-8", newline="\n") as f:
        f.write(f"\n## {now()} — {event} — {owner or 'unassigned'}\n\n{message.strip()}\n")
def update(tid, mutator, event, message):
    with locked():
        ts = tasks()
        if tid not in ts: raise ValueError(f"unknown task {tid}")
        s = status(tid); mutator(s, ts); s["updated_at"] = now(); write_json(STATUS_DIR / f"{tid}.json", s)
        append_log(tid, event, s.get("owner"), message)

def command_validate(_): validate(); print("Project tracking is valid.")
def command_list(args):
    ts = validate(); ss = {t["id"]: status(t["id"]) for t in ts}
    print("ID  STATUS       OWNER          READY  TITLE")
    for t in ts: print(f"{t['id']:<3} {ss[t['id']]['status']:<12} {(ss[t['id']]['owner'] or '-'):14.14} {str(ready(t,ss)).lower():<6} {t['title']}")
def command_next(args):
    ts = validate(); ss = {t["id"]: status(t["id"]) for t in ts}; found = [t for t in ts if ready(t, ss)]
    print("\n".join(f"{t['id']} {t['title']}" for t in found) if found else "No ready tasks.")
def command_claim(args):
    if not args.owner.strip(): raise ValueError("owner must not be blank")
    def mutate(s, ts):
        if s["status"] != "TODO": raise ValueError("only TODO tasks can be claimed")
        if any(status(d)["status"] != "DONE" for d in ts[args.task]["dependencies"]): raise ValueError("dependencies are not DONE")
        s.update(status="IN_PROGRESS", owner=args.owner.strip(), started_at=now(), blocked_reason=None)
    update(args.task, mutate, "CLAIM", "Task claimed.")
def command_note(args):
    if not args.message.strip(): raise ValueError("message must not be blank")
    def mutate(s, _):
        if s["status"] not in {"IN_PROGRESS", "BLOCKED"}: raise ValueError("task must be active")
    update(args.task, mutate, "NOTE", args.message)
def command_block(args):
    if not args.reason.strip(): raise ValueError("reason must not be blank")
    def mutate(s, _):
        if s["status"] != "IN_PROGRESS": raise ValueError("task must be IN_PROGRESS")
        s.update(status="BLOCKED", blocked_reason=args.reason.strip())
    update(args.task, mutate, "BLOCK", args.reason)
def command_unblock(args):
    def mutate(s, _):
        if s["status"] != "BLOCKED": raise ValueError("task must be BLOCKED")
        if s.get("owner") != args.owner: raise ValueError("owner mismatch")
        s.update(status="IN_PROGRESS", blocked_reason=None)
    update(args.task, mutate, "UNBLOCK", "Work resumed.")
def command_verify(args):
    if not args.evidence.strip(): raise ValueError("evidence must not be blank")
    def mutate(s, _):
        if s["status"] != "IN_PROGRESS": raise ValueError("task must be IN_PROGRESS")
        if args.criterion not in s["acceptance"]: raise ValueError("unknown criterion")
        s["acceptance"][args.criterion] = {"verified": True, "evidence": args.evidence.strip()}
    update(args.task, mutate, "VERIFY", f"{args.criterion}: {args.evidence}")
def command_complete(args):
    if not args.evidence.strip(): raise ValueError("evidence must not be blank")
    def mutate(s, _):
        if s["status"] != "IN_PROGRESS": raise ValueError("task must be IN_PROGRESS")
        if not all(v["verified"] for v in s["acceptance"].values()): raise ValueError("all acceptance criteria must be verified")
        s["evidence"].append(args.evidence.strip()); s.update(status="DONE", completed_at=now(), blocked_reason=None)
    update(args.task, mutate, "COMPLETE", args.evidence)
def summary_text(ts, ss):
    lines = ["# Task progress (generated — DO NOT EDIT)", "", f"Last refreshed (UTC): `{now()}`", "", "| Task | Description | Dependencies | Status | Owner | Ready |", "|---|---|---|---|---|---|"]
    for t in ts:
        deps = ", ".join(t["dependencies"]) or "—"; s=ss[t["id"]]
        lines.append(f"| {t['id']} | {t['title']} | {deps} | {s['status']} | {s['owner'] or '—'} | {'yes' if ready(t,ss) else 'no'} |")
    counts={x:sum(s["status"]==x for s in ss.values()) for x in STATES}
    lines += ["", "**Counts:** " + ", ".join(f"{x}: {counts[x]}" for x in ("TODO","IN_PROGRESS","BLOCKED","DONE")), "", "**Ready to claim:** " + (", ".join(t["id"] for t in ts if ready(t,ss)) or "none"), "", "Mutable truth: `tracking/task-status/*.json`; generated from `tasks/manifest.json`.", ""]
    return "\n".join(lines)
def command_summary(args):
    with locked():
        ts=validate(); ss={t["id"]:status(t["id"]) for t in ts}; (ROOT/"tracking"/"SUMMARY.md").write_text(summary_text(ts,ss),encoding="utf-8")
    print("Updated tracking/SUMMARY.md")

def parser():
    p=argparse.ArgumentParser(); sub=p.add_subparsers(required=True)
    for name, fn in (("validate",command_validate),("list",command_list),("next",command_next),("summary",command_summary)): sub.add_parser(name).set_defaults(fn=fn)
    q=sub.add_parser("claim"); q.add_argument("task"); q.add_argument("--owner",required=True); q.set_defaults(fn=command_claim)
    q=sub.add_parser("note"); q.add_argument("task"); q.add_argument("--message",required=True); q.set_defaults(fn=command_note)
    q=sub.add_parser("block"); q.add_argument("task"); q.add_argument("--reason",required=True); q.set_defaults(fn=command_block)
    q=sub.add_parser("unblock"); q.add_argument("task"); q.add_argument("--owner",required=True); q.set_defaults(fn=command_unblock)
    q=sub.add_parser("verify"); q.add_argument("task"); q.add_argument("--criterion",required=True); q.add_argument("--evidence",required=True); q.set_defaults(fn=command_verify)
    q=sub.add_parser("complete"); q.add_argument("task"); q.add_argument("--evidence",required=True); q.set_defaults(fn=command_complete)
    return p
if __name__ == "__main__":
    try:
        a=parser().parse_args(); a.fn(a)
    except (ValueError, RuntimeError, OSError, json.JSONDecodeError) as e:
        print(f"error: {e}", file=sys.stderr); raise SystemExit(2)
