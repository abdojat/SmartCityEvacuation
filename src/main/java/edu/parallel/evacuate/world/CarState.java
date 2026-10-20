package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import java.util.Objects;

/** Immutable engine-side state for an active car. */
public record CarState(int id, GridPosition position, int waitTicks) {
    public CarState {
        if (id < 0 || waitTicks < 0) {
            throw new IllegalArgumentException("id and waitTicks must be nonnegative");
        }
        Objects.requireNonNull(position, "position");
    }
}
