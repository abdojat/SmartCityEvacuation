package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Dependency-free acceptance harness for environments where Maven is unavailable. */
public final class ManualWorldHarness {
    private ManualWorldHarness() {}

    public static void main(String[] args) {
        WorldGenerator generator = new WorldGenerator();
        ScenarioConfig config = new ScenarioConfig(25, 18, 80, 4, .27, 991827L,
                OptionalInt.of(20), 500);
        WorldState first = generator.generate(config);
        WorldState second = generator.generate(config);
        check(first.fingerprint().equals(second.fingerprint()), "repeatable fingerprint");
        check(first.cars().equals(second.cars()), "repeatable cars");
        check(first.blocked().equals(second.blocked()), "repeatable topology");

        HashSet<GridPosition> occupied = new HashSet<>();
        for (CarState car : first.cars()) {
            check(occupied.add(car.position()), "unique occupancy");
            check(reachesExit(first.snapshot(), car.position()), "car reaches exit");
        }
        expectUnsupported(() -> first.snapshot().cars().clear());
        expectUnsupported(() -> first.snapshot().blocked().clear());

        WorldState closable = new WorldState(3, 2, 2,
                List.of(new CarState(0, new GridPosition(0, 1), 0)), Set.of(),
                Set.of(new GridPosition(2, 1)), OptionalInt.of(2), false);
        ClosureResult applied = closable.applyScheduledClosure();
        check(applied.applied(), "closure applied");
        check(applied.closedCell().orElseThrow().equals(new GridPosition(0, 0)), "first safe cell selected");
        check(!closable.closureApplied() && applied.state().closureApplied(), "closure is immutable");

        WorldState corridor = new WorldState(2, 1, 2,
                List.of(new CarState(0, new GridPosition(0, 0), 0)), Set.of(),
                Set.of(new GridPosition(1, 0)), OptionalInt.of(2), false);
        check(!corridor.applyScheduledClosure().applied(), "unsafe closure deferred");
        check(!corridor.next(corridor.cars()).applyScheduledClosure().applied(), "deferred closure retried");

        boolean rejected = false;
        try {
            generator.generate(new ScenarioConfig(2, 2, 2, 1, .75, 1,
                    OptionalInt.empty(), 10));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "impossible input rejected");
        System.out.println("ManualWorldHarness: 12 checks passed");
    }

    private static boolean reachesExit(WorldSnapshot snapshot, GridPosition start) {
        ArrayDeque<GridPosition> queue = new ArrayDeque<>();
        HashSet<GridPosition> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            GridPosition current = queue.removeFirst();
            if (snapshot.exits().contains(current)) return true;
            int x = current.x(), y = current.y();
            GridPosition[] neighbors = {
                    y > 0 ? new GridPosition(x, y - 1) : null,
                    x + 1 < snapshot.width() ? new GridPosition(x + 1, y) : null,
                    y + 1 < snapshot.height() ? new GridPosition(x, y + 1) : null,
                    x > 0 ? new GridPosition(x - 1, y) : null
            };
            for (GridPosition neighbor : neighbors) {
                if (neighbor != null && snapshot.isTraversable(neighbor) && seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return false;
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("collection mutation was accepted");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
