package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Repeatable seeded generator that places exits and cars in one connected component. */
public final class WorldGenerator {
    private static final int MAX_ATTEMPTS = 1_000;

    public WorldState generate(ScenarioConfig config) {
        Objects.requireNonNull(config, "config");
        int cellCount = Math.multiplyExact(config.width(), config.height());
        int obstacleCount = (int) Math.floor(cellCount * config.obstacleDensity());
        if (cellCount - obstacleCount < config.exitCount() + config.carCount()) {
            throw new IllegalArgumentException("density leaves too few traversable cells for exits and cars");
        }
        Random random = new Random(config.seed());
        ArrayList<Integer> ids = new ArrayList<>(cellCount);
        for (int id = 0; id < cellCount; id++) ids.add(id);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Collections.shuffle(ids, random);
            HashSet<GridPosition> blocked = new HashSet<>();
            for (int i = 0; i < obstacleCount; i++) blocked.add(position(ids.get(i), config.width()));
            List<GridPosition> component = largestComponent(config.width(), config.height(), blocked);
            if (component.size() < config.exitCount() + config.carCount()) continue;
            Collections.shuffle(component, random);
            HashSet<GridPosition> exits = new HashSet<>(component.subList(0, config.exitCount()));
            ArrayList<CarState> cars = new ArrayList<>(config.carCount());
            for (int i = 0; i < config.carCount(); i++) {
                cars.add(new CarState(i, component.get(config.exitCount() + i), 0));
            }
            return new WorldState(config.width(), config.height(), 0, cars, blocked, exits,
                    config.closureTick(), false);
        }
        throw new IllegalArgumentException("unable to generate a connected placement after " + MAX_ATTEMPTS + " attempts");
    }

    private static List<GridPosition> largestComponent(int width, int height, Set<GridPosition> blocked) {
        HashSet<GridPosition> visited = new HashSet<>();
        List<GridPosition> largest = List.of();
        for (int id = 0; id < Math.multiplyExact(width, height); id++) {
            GridPosition start = position(id, width);
            if (blocked.contains(start) || visited.contains(start)) continue;
            ArrayList<GridPosition> component = new ArrayList<>();
            ArrayDeque<GridPosition> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                GridPosition current = queue.removeFirst();
                component.add(current);
                int x = current.x(), y = current.y();
                if (y > 0) add(new GridPosition(x, y - 1), blocked, visited, queue);
                if (x + 1 < width) add(new GridPosition(x + 1, y), blocked, visited, queue);
                if (y + 1 < height) add(new GridPosition(x, y + 1), blocked, visited, queue);
                if (x > 0) add(new GridPosition(x - 1, y), blocked, visited, queue);
            }
            if (component.size() > largest.size()) largest = component;
        }
        return new ArrayList<>(largest);
    }

    private static void add(GridPosition position, Set<GridPosition> blocked,
                            Set<GridPosition> visited, ArrayDeque<GridPosition> queue) {
        if (!blocked.contains(position) && visited.add(position)) queue.addLast(position);
    }

    private static GridPosition position(int cellId, int width) {
        return new GridPosition(cellId % width, cellId / width);
    }
}
