package edu.parallel.evacuate.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AStarPathfinderTest {
    private final PathFinder pathFinder = new AStarPathfinder();

    @Test
    void returnsAnOptimalRouteAroundObstacles() {
        WorldSnapshot snapshot = snapshot(5, 4, Set.of(
                p(1, 0), p(1, 1), p(1, 2), p(3, 1), p(3, 2)), Set.of(p(4, 0)), p(0, 0));

        List<GridPosition> route = follow(snapshot, p(0, 0));

        assertEquals(10, route.size());
        assertEquals(p(4, 0), route.getLast());
    }

    @Test
    void usesNorthEastSouthWestAndCellIdOrderingForEqualPaths() {
        WorldSnapshot northVersusSouth = snapshot(3, 3, Set.of(p(1, 1)), Set.of(p(2, 1)), p(0, 1));
        WorldSnapshot eastVersusWest = snapshot(3, 3, Set.of(p(1, 1)), Set.of(p(1, 2)), p(1, 0));

        assertEquals(Optional.of(p(0, 0)), pathFinder.nextStep(northVersusSouth,
                new CarState(0, p(0, 1), 0)));
        assertEquals(Optional.of(p(0, 0)), pathFinder.nextStep(eastVersusWest,
                new CarState(0, p(1, 0), 0)));
    }

    @Test
    void choosesLowestCellIdExitWhenDistancesTie() {
        WorldSnapshot snapshot = snapshot(3, 3, Set.of(), Set.of(p(0, 1), p(2, 1)), p(1, 1));

        assertEquals(Optional.of(p(0, 1)), pathFinder.nextStep(snapshot,
                new CarState(0, p(1, 1), 0)));
    }

    @Test
    void ignoresVehicleOccupancyDuringPlanning() {
        WorldSnapshot snapshot = new WorldSnapshot(3, 1, 0,
                List.of(new CarState(0, p(0, 0), 0), new CarState(1, p(1, 0), 0)),
                Set.of(), Set.of(p(2, 0)), OptionalInt.empty(), false, "fixture");

        assertEquals(Optional.of(p(1, 0)), pathFinder.nextStep(snapshot, snapshot.cars().getFirst()));
    }

    @Test
    void returnsEmptyAtExitOrWhenNoExitIsReachable() {
        WorldSnapshot atExit = snapshot(2, 1, Set.of(), Set.of(p(1, 0)), p(1, 0));
        WorldSnapshot unreachable = snapshot(3, 3,
                Set.of(p(0, 1), p(1, 1), p(2, 1)), Set.of(p(2, 2)), p(0, 0));

        assertTrue(pathFinder.nextStep(atExit, new CarState(0, p(1, 0), 0)).isEmpty());
        assertTrue(pathFinder.nextStep(unreachable, new CarState(0, p(0, 0), 0)).isEmpty());
    }

    @Test
    void repeatedCallsProduceTheSameStep() {
        WorldSnapshot snapshot = snapshot(5, 5, Set.of(p(2, 1), p(2, 2), p(2, 3)),
                Set.of(p(0, 4), p(4, 4)), p(2, 0));
        CarState car = snapshot.cars().getFirst();

        Optional<GridPosition> expected = pathFinder.nextStep(snapshot, car);
        for (int repetition = 0; repetition < 100; repetition++) {
            assertEquals(expected, pathFinder.nextStep(snapshot, car));
        }
    }

    private List<GridPosition> follow(WorldSnapshot original, GridPosition start) {
        List<GridPosition> route = new ArrayList<>();
        GridPosition current = start;
        while (!original.exits().contains(current)) {
            current = pathFinder.nextStep(original, new CarState(0, current, 0)).orElseThrow();
            route.add(current);
            assertTrue(route.size() <= original.width() * original.height(), "route must terminate");
        }
        return route;
    }

    private static WorldSnapshot snapshot(int width, int height, Set<GridPosition> blocked,
                                          Set<GridPosition> exits, GridPosition car) {
        return new WorldSnapshot(width, height, 0, List.of(new CarState(0, car, 0)),
                blocked, exits, OptionalInt.empty(), false, "fixture");
    }

    private static GridPosition p(int x, int y) {
        return new GridPosition(x, y);
    }
}
