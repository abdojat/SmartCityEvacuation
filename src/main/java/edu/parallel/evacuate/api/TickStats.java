package edu.parallel.evacuate.api;

public record TickStats(long tick, int remaining, int evacuated, int moved,
                        int blockedOrConflicted, int maxWaitTicks) {
    public TickStats {
        if (tick < 0 || remaining < 0 || evacuated < 0 || moved < 0 || blockedOrConflicted < 0 || maxWaitTicks < 0)
            throw new IllegalArgumentException("statistics must be nonnegative");
    }
}
