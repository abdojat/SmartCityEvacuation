package edu.parallel.evacuate.world;

import static org.junit.jupiter.api.Assertions.*;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldModelTest {
    private final WorldGenerator generator = new WorldGenerator();

    @Test
    void sameSeedAndConfigurationProduceIdenticalWorld() {
        ScenarioConfig config = new ScenarioConfig(25, 18, 80, 4, .27, 991827L,
                OptionalInt.of(20), 500);
        WorldState first = generator.generate(config);
        WorldState second = generator.generate(config);

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.cars(), second.cars());
        assertEquals(first.blocked(), second.blocked());
        assertEquals(first.exits(), second.exits());
        assertEquals(64, first.fingerprint().length());
    }

    @Test
    void placementsAreUniqueValidAndReachable() {
        ScenarioConfig config = new ScenarioConfig(30, 20, 150, 5, .35, 42L,
                OptionalInt.empty(), 500);
        WorldState state = generator.generate(config);
        Set<GridPosition> occupied = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (CarState car : state.cars()) {
            assertTrue(ids.add(car.id()));
            assertTrue(occupied.add(car.position()));
            assertFalse(state.blocked().contains(car.position()));
            assertFalse(state.exits().contains(car.position()));
            assertTrue(reachesExit(state.snapshot(), car.position()));
        }
        assertEquals(config.carCount(), state.cars().size());
        assertEquals(config.exitCount(), state.exits().size());
    }

    @Test
    void snapshotsAndStateCollectionsAreImmutableAndDetached() {
        var cars = new java.util.ArrayList<>(List.of(new CarState(0, new GridPosition(0, 0), 0)));
        var exits = new HashSet<>(Set.of(new GridPosition(1, 0)));
        WorldState state = new WorldState(2, 1, 0, cars, Set.of(), exits,
                OptionalInt.empty(), false);
        WorldSnapshot snapshot = state.snapshot();
        cars.clear();
        exits.clear();

        assertEquals(1, snapshot.cars().size());
        assertEquals(1, snapshot.exits().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.cars().add(new CarState(1, new GridPosition(0, 0), 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.blocked().add(new GridPosition(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> state.cars().clear());
    }

    @Test
    void closureUsesFirstSafeCellIdAndDoesNotMutateOriginal() {
        WorldState state = new WorldState(3, 2, 2,
                List.of(new CarState(0, new GridPosition(0, 1), 0)), Set.of(),
                Set.of(new GridPosition(2, 1)), OptionalInt.of(2), false);
        ClosureResult result = state.applyScheduledClosure();

        assertTrue(result.applied());
        assertEquals(new GridPosition(0, 0), result.closedCell().orElseThrow());
        assertTrue(result.state().blocked().contains(new GridPosition(0, 0)));
        assertTrue(result.state().closureApplied());
        assertFalse(state.closureApplied());
        assertTrue(state.blocked().isEmpty());
        assertNotEquals(state.fingerprint(), result.state().fingerprint());
    }

    @Test
    void closureWaitsUntilScheduledAndDefersWhenNoSafeCandidateExists() {
        WorldState early = new WorldState(2, 1, 1,
                List.of(new CarState(0, new GridPosition(0, 0), 0)), Set.of(),
                Set.of(new GridPosition(1, 0)), OptionalInt.of(2), false);
        assertSame(early, early.applyScheduledClosure().state());

        WorldState due = early.next(early.cars());
        ClosureResult deferred = due.applyScheduledClosure();
        assertFalse(deferred.applied());
        assertSame(due, deferred.state());

        WorldState nextTick = due.next(due.cars());
        assertFalse(nextTick.applyScheduledClosure().applied(), "pending event is retried and safely deferred");
    }

    @Test
    void invalidWorldsAndImpossibleGenerationFailClearly() {
        assertThrows(IllegalArgumentException.class, () -> new WorldState(2, 1, 0,
                List.of(new CarState(0, new GridPosition(0, 0), 0),
                        new CarState(1, new GridPosition(0, 0), 0)),
                Set.of(), Set.of(new GridPosition(1, 0)), OptionalInt.empty(), false));
        assertThrows(IllegalArgumentException.class, () -> new WorldState(1, 1, 0,
                List.of(), Set.of(new GridPosition(0, 0)), Set.of(new GridPosition(0, 0)),
                OptionalInt.empty(), false));
        ScenarioConfig impossible = new ScenarioConfig(2, 2, 2, 1, .75, 1,
                OptionalInt.empty(), 10);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(impossible));
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
            List<GridPosition> neighbors = List.of(
                    new GridPosition(x, Math.max(0, y - 1)),
                    new GridPosition(Math.min(snapshot.width() - 1, x + 1), y),
                    new GridPosition(x, Math.min(snapshot.height() - 1, y + 1)),
                    new GridPosition(Math.max(0, x - 1), y));
            for (GridPosition neighbor : neighbors) {
                if (snapshot.isTraversable(neighbor) && seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return false;
    }
}
