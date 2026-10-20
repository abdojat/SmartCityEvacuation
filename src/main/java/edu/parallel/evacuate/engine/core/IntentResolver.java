package edu.parallel.evacuate.engine.core;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.TickStats;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldSnapshot;
import edu.parallel.evacuate.world.WorldState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Single-threaded deterministic conflict reduction and commit authority. */
public final class IntentResolver {
    private static final Comparator<CarState> PRIORITY = Comparator
            .comparingInt(CarState::waitTicks).reversed()
            .thenComparingInt(CarState::id);

    public Resolution resolve(WorldState tickStartState, WorldSnapshot snapshot,
                              List<MoveIntent> intents, int evacuatedBeforeTick) {
        Objects.requireNonNull(tickStartState, "tickStartState");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(intents, "intents");
        if (evacuatedBeforeTick < 0) throw new IllegalArgumentException("evacuated count must be nonnegative");
        List<CarState> cars = snapshot.cars();
        if (intents.size() != cars.size()) throw new IllegalArgumentException("one intent is required per car");

        Set<GridPosition> occupiedAtStart = new HashSet<>();
        cars.forEach(car -> occupiedAtStart.add(car.position()));
        Map<GridPosition, List<CarState>> contenders = new HashMap<>();
        Map<Integer, GridPosition> validTargets = new HashMap<>();

        for (int index = 0; index < cars.size(); index++) {
            CarState car = cars.get(index);
            MoveIntent intent = Objects.requireNonNull(intents.get(index), "intent");
            if (intent.carId() != car.id()) throw new IllegalArgumentException("intents must match ordered cars");
            if (!intent.isMove()) continue;
            GridPosition target = intent.target().orElseThrow();
            boolean adjacent = car.position().manhattanDistance(target) == 1;
            boolean usable = adjacent && snapshot.isTraversable(target)
                    && (snapshot.exits().contains(target) || !occupiedAtStart.contains(target));
            if (!usable) continue;
            validTargets.put(car.id(), target);
            if (!snapshot.exits().contains(target)) {
                contenders.computeIfAbsent(target, ignored -> new ArrayList<>()).add(car);
            }
        }

        Set<Integer> winners = new HashSet<>();
        for (CarState car : cars) {
            GridPosition target = validTargets.get(car.id());
            if (target != null && snapshot.exits().contains(target)) winners.add(car.id());
        }
        for (List<CarState> targetContenders : contenders.values()) {
            winners.add(targetContenders.stream().min(PRIORITY).orElseThrow().id());
        }

        ArrayList<CarState> nextCars = new ArrayList<>(cars.size());
        int moved = 0;
        int evacuatedThisTick = 0;
        for (CarState car : cars) {
            if (winners.contains(car.id())) {
                moved++;
                GridPosition target = validTargets.get(car.id());
                if (snapshot.exits().contains(target)) evacuatedThisTick++;
                else nextCars.add(new CarState(car.id(), target, 0));
            } else {
                nextCars.add(new CarState(car.id(), car.position(), Math.addExact(car.waitTicks(), 1)));
            }
        }
        WorldState next = tickStartState.next(nextCars);
        int maxWait = nextCars.stream().mapToInt(CarState::waitTicks).max().orElse(0);
        int evacuated = Math.addExact(evacuatedBeforeTick, evacuatedThisTick);
        int blocked = cars.size() - moved;
        return new Resolution(next, new TickStats(next.tick(), nextCars.size(), evacuated,
                moved, blocked, maxWait));
    }
}
