# Test plan

JUnit 5 tests are deterministic and map to task acceptance criteria.

- T01: configuration/value validation, exactly two enum values, tracking-tool unit tests, and an import scan proving engine layers have no JavaFX dependency.
- T02: repeatable generation/fingerprints, immutable snapshots, unique valid placements, reachability, event closure/defer, and invalid inputs.
- T03: optimal fixture routes, N/E/S/W ties, exit-ID ties, obstacles, unreachable cells, and repeatability.
- T04: one-cell movement, collision/swap prevention, exit removal, wait priority/reset, conservation, gridlock/tick limit, and no partial commit.
- T05/T07: compare per-tick fingerprints and semantic statistics for documented seeds at workers 1/2/4/8/16; repeated runs; bounded partitions; cancellation, failure, shutdown, and thread-leak checks.
- T06/T09: controller tests where practical plus a manual JavaFX checklist for start/pause/resume/stop/reset, both modes, validation, errors, and responsiveness.
- T08: CLI validation, warmup exclusion, CSV schema/row tests, correctness fingerprints, and headless operation.

Required commands are `py -m unittest tools.tests` (or `python` where available), `mvn test`, a documented small benchmark invocation, and `mvn javafx:run` for manual UI verification. Record actual output; unavailable tools or displays are explicitly `not verified`. No performance test asserts a speedup.
