# Headless benchmarks

Build, then run the CLI without JavaFX rendering:

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests package
java -cp target/classes edu.parallel.evacuate.benchmark.BenchmarkCli --output benchmarks/results.csv --scenario small --width 24 --height 18 --cars 80 --density 0.12 --exits 3 --seed 101 --closure-tick 10 --tick-limit 1000 --warmups 2 --repetitions 5 --workers 1,2,4,8,16
```

The CSV contains raw warmup and measured rows. Filter `warmup=false` for reported timing summaries. Each repetition is counterbalanced by reversing case order, and export is rejected if comparable sequential/parallel final fingerprints differ. Generation and CSV writing occur outside the engine's elapsed timer.

Commit generated CSV only when it is intentionally used as report evidence; never substitute invented values.
