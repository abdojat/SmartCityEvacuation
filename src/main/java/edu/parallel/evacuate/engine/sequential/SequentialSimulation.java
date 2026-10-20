package edu.parallel.evacuate.engine.sequential;

import edu.parallel.evacuate.engine.core.SimulationEngine;
import edu.parallel.evacuate.pathfinding.AStarPathfinder;
import edu.parallel.evacuate.pathfinding.PathFinder;
import edu.parallel.evacuate.world.WorldState;

/** Sequential simulation facade using the shared atomic engine pipeline. */
public final class SequentialSimulation extends SimulationEngine {
    public SequentialSimulation(WorldState initialState, int tickLimit) {
        this(initialState, new AStarPathfinder(), tickLimit);
    }

    public SequentialSimulation(WorldState initialState, PathFinder pathFinder, int tickLimit) {
        super(initialState, new SequentialIntentComputer(pathFinder), tickLimit);
    }
}
