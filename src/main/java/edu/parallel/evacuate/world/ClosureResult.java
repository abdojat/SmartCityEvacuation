package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import java.util.Objects;
import java.util.Optional;

/** Result of attempting the scheduled deterministic road closure. */
public record ClosureResult(WorldState state, Optional<GridPosition> closedCell) {
    public ClosureResult {
        Objects.requireNonNull(state, "state");
        closedCell = Objects.requireNonNull(closedCell, "closedCell");
    }

    public boolean applied() {
        return closedCell.isPresent();
    }
}
