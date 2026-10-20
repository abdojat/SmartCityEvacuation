package edu.parallel.evacuate.pathfinding;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.Optional;

/** Computes one movement step without mutating the tick-start snapshot. */
@FunctionalInterface
public interface PathFinder {
    Optional<GridPosition> nextStep(WorldSnapshot snapshot, CarState car);
}
