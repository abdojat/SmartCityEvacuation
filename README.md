# Smart City Emergency Evacuation Simulator

A deterministic Java 21 grid-evacuation simulator with exactly two execution modes: sequential and parallel. Both modes use the same immutable tick snapshot, A* routing, conflict resolver, and commit rules; parallel mode changes only how movement intents are computed.

## Run

Requirements: JDK 21 and Maven. This workspace also has an ignored local Maven distribution at `.tools/apache-maven-3.9.9`.

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd clean test
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd javafx:run
```

The UI configures grid size, cars, exits, obstacle density, seed, closure tick, tick limit, mode, and parallel workers. It supports start, pause/resume, stop, and reset and displays the grid and live metrics. Simulation work runs off the JavaFX Application Thread.

## Headless benchmark

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests package
java -cp target/classes edu.parallel.evacuate.benchmark.BenchmarkCli --output benchmarks/results.csv --scenario small --width 24 --height 18 --cars 80 --density 0.12 --exits 3 --seed 101 --closure-tick 10 --tick-limit 1000 --warmups 2 --repetitions 5 --workers 1,2,4,8,16
```

CSV includes environment/configuration, warmup flag, timing, outcome, and final SHA-256 fingerprint. Comparable runs are rejected if fingerprints differ. UI rendering and world generation are excluded from engine timing.

## Design and evidence

Cars compute A* next steps against one immutable tick-start snapshot. Parallel workers fill disjoint indexed slots in stable partitions; a single deterministic resolver rejects invalid/occupied-at-start moves and selects conflicts by wait count descending then car ID ascending. The fixed pool is reused and cancelled/shut down boundedly.

- Specifications: [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/CONCURRENCY.md](docs/CONCURRENCY.md)
- Test and benchmark plans: [docs/TEST_PLAN.md](docs/TEST_PLAN.md), [docs/BENCHMARK_PLAN.md](docs/BENCHMARK_PLAN.md)
- Technical report: [report/TECHNICAL_REPORT.md](report/TECHNICAL_REPORT.md)
- Raw measured data: [benchmarks/results](benchmarks/results) and [benchmarks/smoke-2026-10-20.csv](benchmarks/smoke-2026-10-20.csv)
- Progress: [tracking/SUMMARY.md](tracking/SUMMARY.md), [tracking/HANDOFF.md](tracking/HANDOFF.md)

Traffic can gridlock because a car may not enter a road cell occupied at tick start, even if that occupant plans to leave. This deliberate physics rule avoids swaps/cycles; it is unrelated to thread deadlock.
