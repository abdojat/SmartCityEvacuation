package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Central invariant validation used by generation and every future commit. */
public final class WorldValidator {
    private WorldValidator() {}

    public static void validate(int width, int height, long tick, List<CarState> cars,
                                Set<GridPosition> blocked, Set<GridPosition> exits,
                                OptionalInt closureTick, boolean closureApplied) {
        if (width <= 0 || height <= 0 || tick < 0) throw new IllegalArgumentException("invalid world size or tick");
        if (exits.isEmpty()) throw new IllegalArgumentException("at least one exit is required");
        if (closureTick.isPresent() && closureTick.getAsInt() < 0) throw new IllegalArgumentException("closure tick must be nonnegative");
        if (closureApplied && closureTick.isEmpty()) throw new IllegalArgumentException("unscheduled closure cannot be applied");
        HashSet<Integer> ids = new HashSet<>();
        HashSet<GridPosition> occupied = new HashSet<>();
        int previousId = -1;
        for (GridPosition position : blocked) requireInside(width, height, position);
        for (GridPosition exit : exits) {
            requireInside(width, height, exit);
            if (blocked.contains(exit)) throw new IllegalArgumentException("exit cannot be blocked");
        }
        for (CarState car : cars) {
            requireInside(width, height, car.position());
            if (!ids.add(car.id()) || !occupied.add(car.position())) throw new IllegalArgumentException("duplicate car id or occupancy");
            if (car.id() <= previousId) throw new IllegalArgumentException("cars must be ordered by id");
            if (blocked.contains(car.position()) || exits.contains(car.position())) throw new IllegalArgumentException("car on blocked cell or exit");
            previousId = car.id();
        }
    }

    private static void requireInside(int width, int height, GridPosition position) {
        if (position.x() >= width || position.y() >= height) throw new IllegalArgumentException("position outside grid");
    }
}
