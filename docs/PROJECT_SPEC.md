# Project specification

## Purpose and scope
Build a desktop and headless simulator that demonstrates deterministic emergency traffic evacuation on a rectangular city grid. Java 21, Maven, and JavaFX are required. The only execution modes are `SEQUENTIAL` and `PARALLEL`; they differ solely in intent computation.

## Functional requirements
- Generate repeatable seeded grids containing traversable roads, blocked cells, exits, and uniquely placed cars. Every initial car must have a route to an exit.
- Advance in discrete ticks using the pipeline in `ARCHITECTURE.md`, including an optional deterministic road closure.
- Provide start, pause/resume, stop, and reset controls; configuration validation; visualization; live tick/session metrics; and visible failures.
- Run benchmarks without JavaFX rendering and export raw measurements to CSV.
- Produce identical per-tick state fingerprints and results for equal configurations in both modes at worker counts 1, 2, 4, 8, and 16.

## Configuration
`ScenarioConfig` defines positive width/height, nonnegative car count, obstacle density in `[0,1)`, positive exit count not exceeding cell count, seed, optional closure tick, and a positive tick limit. Generation fails clearly when constraints cannot be satisfied. Defaults belong in the UI/CLI, not the engine contract.

## Quality requirements
The engine is independent of JavaFX, validates invariants after commits, never partially commits a failed tick, reuses a bounded executor, shuts down safely, and never shares mutable random state. UI work happens on the JavaFX Application Thread while simulation work does not. Performance claims use measured data only.

## Non-goals
No databases, real maps, networking, distributed execution, machine learning/GPU work, diagonal movement, traffic-light optimization, or third execution mode. Traffic gridlock may be represented; thread deadlock may not.

## Repository definition of done
All task acceptance criteria are evidenced; `mvn test` passes on Java 21; the JavaFX app can run and stop both modes; headless benchmarks export validated CSV; cross-mode determinism/collision tests pass; and the 4–6 page report describes actual measurements and limitations.
