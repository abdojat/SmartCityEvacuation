# API contracts

Package root is `edu.parallel.evacuate`. Public records defensively copy collections and reject invalid/null input.

## Stable shared API (owned by T01)
- `api.ExecutionMode`: exactly `SEQUENTIAL`, `PARALLEL`.
- `api.GridPosition(int x, int y)`: value object; cell ID is `y * width + x`.
- `api.ScenarioConfig`: validated dimensions, cars, exits, obstacle density, seed, optional closure tick, and tick limit.
- `api.CarView(int id, GridPosition position, int waitTicks)` and immutable `api.SimulationSnapshot` for consumers.
- `api.TickStats` and `api.SessionStats`; elapsed values use nanoseconds and counts are nonnegative.
- `api.Simulation`: tick/run/pause/resume/cancel/snapshot/close lifecycle; `close` is idempotent.

## Internal contracts
- `world.WorldSnapshot` is a complete immutable tick-start view. Cars are ordered by ascending ID. Exits are traversable and remove entrants without an occupancy limit.
- `pathfinding.PathFinder.nextStep(snapshot, car)` returns a next position or empty if already at an exit/unreachable. Planning ignores cars but respects blocked cells.
- `engine.core.MoveIntent` identifies one car and either an adjacent target or a typed STAY reason.
- `engine.core.IntentComputer` computes one indexed result per ordered car without mutating the snapshot.
- `engine.core.IntentResolver` is the sole commit authority. Non-exit targets occupied at tick start are invalid. Conflict priority is wait count descending, then car ID ascending. Winners reset wait to zero; active losers/stayers increment it once.

## Determinism and fingerprints
Neighbors are examined N/E/S/W. A* ordering is `(f, h, cellId)`; equal-distance reachable exits are selected by exit cell ID. Fingerprints are SHA-256 over version, dimensions, sorted blocked cell IDs, sorted exit IDs, tick/event state, and active cars sorted as `(id,cellId,waitTicks)`. Timing and worker count are excluded.

At most `workerCount` deterministic contiguous partitions are submitted per tick. Workers only read the snapshot and fill disjoint indexed result slots. Core, world, pathfinding, and benchmark packages must not import JavaFX.
