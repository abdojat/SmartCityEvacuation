package edu.parallel.evacuate.engine.core;

import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.List;

/** Computes one indexed, car-ID-ordered intent per snapshot car. */
@FunctionalInterface
public interface IntentComputer extends AutoCloseable {
    List<MoveIntent> compute(WorldSnapshot snapshot);

    /** Requests cancellation of current work without permanently closing this computer. */
    default void cancelActive() { }

    @Override
    default void close() { }
}
