package edu.parallel.evacuate.benchmark;

import edu.parallel.evacuate.api.ExecutionMode;
import java.time.Instant;

public record BenchmarkRow(int schemaVersion, Instant timestamp, String javaVersion, String jvm,
                           String os, String cpu, String scenario, int width, int height,
                           int cars, double density, int exits, long seed, ExecutionMode mode,
                           int workers, int repetition, boolean warmup, long ticks,
                           int evacuated, int remaining, long elapsedNanos,
                           double ticksPerSecond, String finalFingerprint) {
    public static final int SCHEMA_VERSION = 1;
}
