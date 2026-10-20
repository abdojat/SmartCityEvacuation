# Architecture decisions

## D001 — Deterministic tick boundary
Accepted: workers compute intents from one immutable snapshot; one coordinator resolves and commits.

## D002 — UI dependency
Accepted: T06 depends on T05 so its two-mode UI is complete when accepted.

## D003 — Canonical task states
Accepted: tracking JSON and generated summaries use `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE`.
