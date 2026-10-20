package edu.parallel.evacuate.engine.parallel;

import edu.parallel.evacuate.engine.core.SimulationEngine;
import edu.parallel.evacuate.pathfinding.AStarPathfinder;
import edu.parallel.evacuate.pathfinding.PathFinder;
import edu.parallel.evacuate.world.WorldState;

/** Parallel simulation facade; only the intent stage differs from sequential mode. */
public final class ParallelSimulation extends SimulationEngine {
    public ParallelSimulation(WorldState initialState, int workerCount, int tickLimit) {
        this(initialState, new AStarPathfinder(), workerCount, tickLimit);
    }

    public ParallelSimulation(WorldState initialState, PathFinder pathFinder,
                              int workerCount, int tickLimit) {
        super(initialState, new ParallelIntentComputer(pathFinder, workerCount), tickLimit);
    }
}
