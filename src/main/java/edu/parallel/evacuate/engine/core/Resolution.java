package edu.parallel.evacuate.engine.core;

import edu.parallel.evacuate.api.TickStats;
import edu.parallel.evacuate.world.WorldState;
import java.util.Objects;

/** Fully validated candidate commit and its semantic statistics. */
public record Resolution(WorldState state, TickStats stats) {
    public Resolution {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stats, "stats");
    }
}
