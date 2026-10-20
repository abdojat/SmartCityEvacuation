package edu.parallel.evacuate.engine.sequential;

import edu.parallel.evacuate.engine.core.IntentComputer;
import edu.parallel.evacuate.engine.core.MoveIntent;
import edu.parallel.evacuate.engine.core.StayReason;
import edu.parallel.evacuate.pathfinding.PathFinder;
import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic single-threaded intent computation. */
public final class SequentialIntentComputer implements IntentComputer {
    private final PathFinder pathFinder;

    public SequentialIntentComputer(PathFinder pathFinder) {
        this.pathFinder = Objects.requireNonNull(pathFinder, "pathFinder");
    }

    @Override
    public List<MoveIntent> compute(WorldSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ArrayList<MoveIntent> intents = new ArrayList<>(snapshot.cars().size());
        for (var car : snapshot.cars()) {
            intents.add(pathFinder.nextStep(snapshot, car)
                    .map(target -> MoveIntent.move(car.id(), target))
                    .orElseGet(() -> MoveIntent.stay(car.id(),
                            snapshot.exits().contains(car.position())
                                    ? StayReason.ALREADY_AT_EXIT : StayReason.UNREACHABLE)));
        }
        return List.copyOf(intents);
    }
}
