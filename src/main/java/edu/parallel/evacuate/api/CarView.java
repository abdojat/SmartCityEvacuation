package edu.parallel.evacuate.api;

import java.util.Objects;

public record CarView(int id, GridPosition position, int waitTicks) {
    public CarView {
        if (id < 0 || waitTicks < 0) throw new IllegalArgumentException("id and wait ticks must be nonnegative");
        Objects.requireNonNull(position, "position");
    }
}
