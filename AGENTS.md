# AGENTS.md — Codex operating contract

You are implementing **Smart City Emergency Evacuation Simulator** in this repository. This file is authoritative for *how to work*. Read it before editing any source code and again at the start of a new session.

## Mandatory first reads, in order
1. `docs/PROJECT_SPEC.md` (scope, requirements, explicit non-goals)
2. `docs/ARCHITECTURE.md` + `docs/API_CONTRACTS.md` (interfaces and ownership)
3. `docs/CONCURRENCY.md` (correctness and determinism)
4. `docs/BENCHMARK_PLAN.md` + `docs/TEST_PLAN.md` (required evidence)
5. `tasks/manifest.json`, `tracking/SUMMARY.md`, and the `tasks/Txx.md` for the chosen ready task.

## Source of truth for progress
- `tasks/manifest.json` defines **IDs, prerequisites, ownership, and intended deliverables**. Do not edit it casually.
- `tracking/task-status/Txx.json` is the authoritative **mutable status** for each task.
- `tracking/logs/Txx.md` is that task's append-only working log.
- `tracking/SUMMARY.md` is **generated**, not hand-edited.
- `tracking/HANDOFF.md` is the coordinator-maintained short resumption note.

Use `python tools/project_status.py next` to find ready work, then **claim it** before editing. On any substantive change, log progress. Run `python tools/project_status.py summary` after updates. Never mark `done` without a real evidence string and verified acceptance criteria.

## Task execution loop
1. Read the task instructions and its prerequisites; check `git status` before work.
2. Claim the task: `python tools/project_status.py claim T01 --owner codex-main` (change ID / owner).
3. Implement in the task's owned paths; do not change another agent's files without coordinator approval.
4. Add or update tests and execute relevant commands. Never invent test results.
5. Log milestones: `python tools/project_status.py note T01 --message "Implemented ..."`.
6. Finish: `python tools/project_status.py complete T01 --evidence "mvn test passed; ..."`.
7. If blocked: `python tools/project_status.py block T01 --reason "..."`.
8. Refresh the summary, update `tracking/HANDOFF.md`, choose the next ready task.

## Delegation and parallel work
If Codex supports subagents, the **coordinator** may delegate independent ready tasks in parallel, each using `prompts/CODEX_WORKER_PROMPT.md`. If delegation is unavailable, perform tasks sequentially using the same process. Do not pretend that subagents exist.
- One owner per task at a time. Workers may edit **only task-owned source paths** and that task's `tracking/task-status/Txx.json` and `tracking/logs/Txx.md`.
- Coordinator alone edits `tracking/HANDOFF.md`, `docs/DECISIONS.md`, common interfaces, `tasks/manifest.json`, and integration configuration after T01.
- Never ask two workers to edit the same file simultaneously. Use isolated git branches/worktrees if multiple writing agents share a machine; merge and run tests in the coordinator working tree.
- Subagents must provide paths changed, tests run, and any contract changes proposed. The coordinator integrates and verifies before accepting a task as done.
- If a public API contract needs to change, propose it first; do not silently break another workstream.

## Non-negotiable product rules
- Exactly **two execution modes**: SEQUENTIAL and PARALLEL; never build a third naive/broken mode.
- The simulation physics and conflict resolution must be identical in both modes; only how intents are *computed* may vary.
- Use immutable per-tick snapshots, independently computed movement intents, deterministic conflict reduction/commit. No simultaneous mutation of world state by workers.
- A* must use consistent deterministic tie-breaking. Same seed/configuration => same state hash in both modes at every worker count (1, 2, 4, 8, 16).
- Thread pool reused across ticks; bounded tasks; safe shutdown; no shared Random object; UI updates strictly on JavaFX Application Thread.
- Never deliberately introduce real deadlocks, unsafe race conditions, or broken data for a demo. Explain how the safe design prevents them.
- The engine must have no JavaFX dependency. The GUI never blocks on simulation or benchmarks.
- Performance graphs use **measured** data only; no fabricated speedups.
- Target assignment: source code, short 4–6 page technical report, runtime tables/graphs across thread counts and data sizes, Amdahl's law discussion.

## Definition of done for the entire repository
- `mvn test` passes (on a machine with Java 21 and Maven, when available).
- `mvn javafx:run` launches the app and it can run/stop both modes without freezing.
- Benchmarks can be run headlessly and exported to CSV.
- Determinism and collision invariants pass (seeded scenarios, multiple thread counts).
- Reports describe *actual* results and limitations; there are no placeholder claims.

## Communication and failure policy
Be concise but explicit in task logs. If you cannot run Maven/JavaFX due to the environment, record **not verified**; do not mark tests as passed. If blocked, leave repository in a recoverable state and explain the smallest next action. Do not expand scope with databases, maps, networking, distributed nodes, machine learning, or a third mode.
