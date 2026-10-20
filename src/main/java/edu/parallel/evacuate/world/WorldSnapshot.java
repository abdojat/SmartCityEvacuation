package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** Complete immutable view captured at the start of a logical tick. */
public record WorldSnapshot(int width, int height, long tick, List<CarState> cars,
                            Set<GridPosition> blocked, Set<GridPosition> exits,
                            OptionalInt closureTick, boolean closureApplied,
                            String fingerprint) {
    public WorldSnapshot {
        if (width <= 0 || height <= 0 || tick < 0) {
            throw new IllegalArgumentException("invalid dimensions or tick");
        }
        cars = List.copyOf(Objects.requireNonNull(cars, "cars"));
        blocked = Set.copyOf(Objects.requireNonNull(blocked, "blocked"));
        exits = Set.copyOf(Objects.requireNonNull(exits, "exits"));
        Objects.requireNonNull(closureTick, "closureTick");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public boolean contains(GridPosition position) {
        return position.x() < width && position.y() < height;
    }

    public boolean isTraversable(GridPosition position) {
        return contains(position) && !blocked.contains(position);
    }
}
