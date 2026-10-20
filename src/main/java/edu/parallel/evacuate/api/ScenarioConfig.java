package edu.parallel.evacuate.api;

import java.util.OptionalInt;

public record ScenarioConfig(int width, int height, int carCount, int exitCount,
                             double obstacleDensity, long seed, OptionalInt closureTick,
                             int tickLimit) {
    public ScenarioConfig {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("dimensions must be positive");
        int cells = Math.multiplyExact(width, height);
        if (carCount < 0 || exitCount <= 0 || exitCount > cells || carCount > cells - exitCount)
            throw new IllegalArgumentException("invalid car or exit count");
        if (!Double.isFinite(obstacleDensity) || obstacleDensity < 0 || obstacleDensity >= 1)
            throw new IllegalArgumentException("obstacle density must be in [0,1)");
        if (closureTick == null) throw new NullPointerException("closureTick");
        if (closureTick.isPresent() && closureTick.getAsInt() < 0)
            throw new IllegalArgumentException("closure tick must be nonnegative");
        if (tickLimit <= 0) throw new IllegalArgumentException("tick limit must be positive");
    }
    public static ScenarioConfig defaults(long seed) {
        return new ScenarioConfig(40, 30, 250, 4, .18, seed, OptionalInt.of(20), 10_000);
    }
}
