package edu.parallel.evacuate.engine.sequential;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.engine.core.SimulationEngine;
import edu.parallel.evacuate.pathfinding.AStarPathfinder;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldState;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SequentialSimulationTest {
    @Test
    void advancesOneCellThenRemovesCarAtExit() {
        try (SequentialSimulation simulation = new SequentialSimulation(
                world(3, 1, List.of(new CarState(0, p(0, 0), 0)), Set.of(), Set.of(p(2, 0)), OptionalInt.empty()), 10)) {
            var first = simulation.tick();
            assertEquals(p(1, 0), simulation.snapshot().cars().getFirst().position());
            assertEquals(1, first.moved());
            var second = simulation.tick();
            assertTrue(simulation.snapshot().cars().isEmpty());
            assertEquals(1, second.evacuated());
            assertEquals(2, second.tick());
        }
    }

    @Test
    void runStopsAtVerifiedGridlockAndTickLimit() {
        WorldState gridlock = world(3, 2,
                List.of(new CarState(0, p(0, 0), 0), new CarState(1, p(1, 0), 0)),
                Set.of(p(1, 1), p(2, 0)), Set.of(p(2, 1)), OptionalInt.empty());
        try (SequentialSimulation simulation = new SequentialSimulation(gridlock, 20)) {
            var stats = simulation.run();
            assertEquals(1, stats.ticksProcessed());
            assertEquals(2, stats.remaining());
        }

        WorldState longRoad = world(8, 1, List.of(new CarState(0, p(0, 0), 0)),
                Set.of(), Set.of(p(7, 0)), OptionalInt.empty());
        try (SequentialSimulation simulation = new SequentialSimulation(longRoad, 2)) {
            var stats = simulation.run();
            assertEquals(2, stats.ticksProcessed());
            assertEquals(1, stats.remaining());
        }
    }

    @Test
    void failedIntentStageDoesNotCommitClosureOrTick() {
        WorldState initial = world(4, 2, List.of(new CarState(0, p(0, 0), 0)),
                Set.of(), Set.of(p(3, 0)), OptionalInt.of(0));
        String original = initial.fingerprint();
        SimulationEngine simulation = new SimulationEngine(initial, snapshot -> {
            throw new IllegalStateException("injected failure");
        }, 10);
        assertThrows(IllegalStateException.class, simulation::tick);
        assertEquals(original, simulation.snapshot().fingerprint());
        assertEquals(0, simulation.snapshot().tick());
        simulation.close();
    }

    @Test
    void lifecycleIsConsistentAndCloseIsIdempotent() {
        SequentialSimulation simulation = new SequentialSimulation(
                world(2, 1, List.of(new CarState(0, p(0, 0), 0)), Set.of(), Set.of(p(1, 0)), OptionalInt.empty()), 3);
        simulation.pause();
        assertTrue(simulation.isPaused());
        assertThrows(IllegalStateException.class, simulation::tick);
        simulation.resume();
        assertFalse(simulation.isPaused());
        assertNotEquals(0, simulation.tick().moved());
        simulation.cancel();
        assertTrue(simulation.isCancelled());
        assertThrows(IllegalStateException.class, simulation::tick);
        assertDoesNotThrow(simulation::close);
        assertDoesNotThrow(simulation::close);
        assertThrows(IllegalStateException.class, simulation::run);
    }

    @Test
    void scheduledClosureAndSequentialIntentsRemainDeterministic() {
        WorldState initial = world(5, 2, List.of(new CarState(0, p(0, 0), 0)),
                Set.of(), Set.of(p(4, 0)), OptionalInt.of(0));
        try (SequentialSimulation first = new SequentialSimulation(initial, new AStarPathfinder(), 10);
             SequentialSimulation second = new SequentialSimulation(initial, new AStarPathfinder(), 10)) {
            assertEquals(first.tick(), second.tick());
            assertEquals(first.snapshot().fingerprint(), second.snapshot().fingerprint());
            assertEquals(1, first.snapshot().blocked().size());
        }
    }

    private static WorldState world(int width, int height, List<CarState> cars,
                                    Set<GridPosition> blocked, Set<GridPosition> exits,
                                    OptionalInt closureTick) {
        return new WorldState(width, height, 0, cars, blocked, exits, closureTick, false);
    }
    private static GridPosition p(int x, int y) { return new GridPosition(x, y); }
}
