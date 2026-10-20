package edu.parallel.evacuate.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldState;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntentResolverTest {
    private final IntentResolver resolver = new IntentResolver();

    @Test
    void conflictUsesWaitDescendingThenIdAscendingAndUpdatesWaits() {
        WorldState state = world(3, 2, List.of(
                new CarState(2, p(0, 0), 3),
                new CarState(4, p(2, 0), 3),
                new CarState(7, p(1, 1), 5)), Set.of(p(2, 1)));
        List<MoveIntent> intents = List.of(
                MoveIntent.move(2, p(1, 0)),
                MoveIntent.move(4, p(1, 0)),
                MoveIntent.move(7, p(1, 0)));

        Resolution result = resolver.resolve(state, state.snapshot(), intents, 0);

        assertEquals(List.of(
                new CarState(2, p(0, 0), 4),
                new CarState(4, p(2, 0), 4),
                new CarState(7, p(1, 0), 0)), result.state().cars());
        assertEquals(1, result.stats().moved());
        assertEquals(2, result.stats().blockedOrConflicted());
        assertEquals(4, result.stats().maxWaitTicks());
    }

    @Test
    void equalWaitConflictUsesLowestCarId() {
        WorldState state = world(3, 2, List.of(
                new CarState(2, p(0, 0), 1), new CarState(4, p(2, 0), 1)), Set.of(p(2, 1)));
        Resolution result = resolver.resolve(state, state.snapshot(), List.of(
                MoveIntent.move(2, p(1, 0)), MoveIntent.move(4, p(1, 0))), 0);
        assertEquals(p(1, 0), result.state().cars().get(0).position());
        assertEquals(p(2, 0), result.state().cars().get(1).position());
    }

    @Test
    void occupiedAtStartPreventsSwapAndExitAcceptsMultipleCars() {
        WorldState swap = world(3, 2, List.of(
                new CarState(0, p(0, 0), 0), new CarState(1, p(1, 0), 0)), Set.of(p(2, 1)));
        Resolution blocked = resolver.resolve(swap, swap.snapshot(), List.of(
                MoveIntent.move(0, p(1, 0)), MoveIntent.move(1, p(0, 0))), 0);
        assertEquals(List.of(p(0, 0), p(1, 0)), blocked.state().cars().stream().map(CarState::position).toList());
        assertEquals(0, blocked.stats().moved());

        WorldState exits = world(3, 2, List.of(
                new CarState(0, p(0, 0), 0), new CarState(1, p(1, 1), 2)), Set.of(p(1, 0)));
        Resolution evacuated = resolver.resolve(exits, exits.snapshot(), List.of(
                MoveIntent.move(0, p(1, 0)), MoveIntent.move(1, p(1, 0))), 0);
        assertEquals(0, evacuated.state().cars().size());
        assertEquals(2, evacuated.stats().moved());
        assertEquals(2, evacuated.stats().evacuated());
    }

    @Test
    void rejectsMalformedIntentsWithoutChangingInput() {
        WorldState state = world(2, 1, List.of(new CarState(0, p(0, 0), 0)), Set.of(p(1, 0)));
        String fingerprint = state.fingerprint();
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(state, state.snapshot(), List.of(MoveIntent.move(4, p(1, 0))), 0));
        assertEquals(fingerprint, state.fingerprint());
        assertEquals(0, state.tick());
    }

    private static WorldState world(int width, int height, List<CarState> cars, Set<GridPosition> exits) {
        return new WorldState(width, height, 0, cars, Set.of(), exits, OptionalInt.empty(), false);
    }
    private static GridPosition p(int x, int y) { return new GridPosition(x, y); }
}
