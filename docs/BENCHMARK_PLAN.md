# Benchmark plan

Measure the headless engine only. Use fixed seeds and at least three scenario sizes/densities, both modes, and parallel workers 1/2/4/8/16. Each case uses warmups followed by multiple measured repetitions in a counterbalanced order. Stop on evacuation, verified gridlock, or the configured tick limit.

CSV rows include schema version, timestamp, Java/JVM/OS/CPU metadata, scenario name, width, height, cars, density, exits, seed, mode, workers, repetition, warmup flag, ticks, evacuated/remaining, elapsed nanoseconds, ticks/second, and final fingerprint. Raw rows are retained. Tables report median and dispersion, speedup against sequential, efficiency, and observed serial fraction/Amdahl interpretation.

Setup/generation and JavaFX rendering are excluded from elapsed engine time. Comparable rows must have matching configurations and final fingerprints before timing comparisons are accepted. No hard speedup threshold is a correctness criterion. Generated graphs must be traceable to committed or identified CSV input; placeholder numbers are forbidden.
