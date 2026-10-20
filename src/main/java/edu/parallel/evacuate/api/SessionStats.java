package edu.parallel.evacuate.api;

import java.util.Objects;

public record SessionStats(long elapsedNanos, long ticksProcessed, int evacuated,
                           int remaining, String finalFingerprint) {
    public SessionStats {
        if (elapsedNanos < 0 || ticksProcessed < 0 || evacuated < 0 || remaining < 0)
            throw new IllegalArgumentException("statistics must be nonnegative");
        Objects.requireNonNull(finalFingerprint, "finalFingerprint");
    }
    public double ticksPerSecond() { return elapsedNanos == 0 ? 0 : ticksProcessed * 1_000_000_000.0 / elapsedNanos; }
}
