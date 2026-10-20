package edu.parallel.evacuate.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SimulationSnapshot(int width, int height, long tick, List<CarView> cars,
                                 Set<GridPosition> blocked, Set<GridPosition> exits,
                                 String fingerprint) {
    public SimulationSnapshot {
        if (width <= 0 || height <= 0 || tick < 0) throw new IllegalArgumentException("invalid dimensions or tick");
        cars = List.copyOf(Objects.requireNonNull(cars, "cars"));
        blocked = Set.copyOf(Objects.requireNonNull(blocked, "blocked"));
        exits = Set.copyOf(Objects.requireNonNull(exits, "exits"));
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
