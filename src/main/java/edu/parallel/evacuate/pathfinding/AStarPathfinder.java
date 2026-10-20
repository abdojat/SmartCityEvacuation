package edu.parallel.evacuate.pathfinding;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/** Deterministic, optimal four-direction A* pathfinder for immutable snapshots. */
public final class AStarPathfinder implements PathFinder {
    private static final int UNREACHED = Integer.MAX_VALUE;

    private record Node(int cellId, int g, int h) {
        int f() {
            return Math.addExact(g, h);
        }
    }

    private static final Comparator<Node> NODE_ORDER = Comparator
            .comparingInt(Node::f)
            .thenComparingInt(Node::h)
            .thenComparingInt(Node::cellId);

    @Override
    public Optional<GridPosition> nextStep(WorldSnapshot snapshot, CarState car) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(car, "car");
        GridPosition start = car.position();
        if (!snapshot.contains(start) || !snapshot.isTraversable(start)) {
            return Optional.empty();
        }
        if (snapshot.exits().contains(start)) {
            return Optional.empty();
        }

        int width = snapshot.width();
        int cellCount = Math.multiplyExact(width, snapshot.height());
        int startId = start.cellId(width);
        int[] bestG = new int[cellCount];
        int[] parent = new int[cellCount];
        Arrays.fill(bestG, UNREACHED);
        Arrays.fill(parent, -1);

        PriorityQueue<Node> open = new PriorityQueue<>(NODE_ORDER);
        bestG[startId] = 0;
        open.add(new Node(startId, 0, heuristic(start, snapshot)));
        int goalDistance = UNREACHED;
        int goalId = -1;

        while (!open.isEmpty()) {
            Node current = open.remove();
            if (current.g() != bestG[current.cellId()]) {
                continue;
            }
            if (current.f() > goalDistance) {
                break;
            }
            GridPosition position = position(current.cellId(), width);
            if (snapshot.exits().contains(position)) {
                goalDistance = current.g();
                if (goalId < 0 || current.cellId() < goalId) {
                    goalId = current.cellId();
                }
                continue;
            }
            addNeighbors(position, current, snapshot, bestG, parent, open);
        }
        return goalId < 0
                ? Optional.empty()
                : Optional.of(firstStep(goalId, startId, parent, width));
    }

    private static void addNeighbors(GridPosition position, Node current, WorldSnapshot snapshot,
                                     int[] bestG, int[] parent, PriorityQueue<Node> open) {
        int x = position.x();
        int y = position.y();
        // Contractual neighbor order: north, east, south, west.
        consider(x, y - 1, current, snapshot, bestG, parent, open);
        consider(x + 1, y, current, snapshot, bestG, parent, open);
        consider(x, y + 1, current, snapshot, bestG, parent, open);
        consider(x - 1, y, current, snapshot, bestG, parent, open);
    }

    private static void consider(int x, int y, Node current, WorldSnapshot snapshot,
                                 int[] bestG, int[] parent, PriorityQueue<Node> open) {
        if (x < 0 || y < 0 || x >= snapshot.width() || y >= snapshot.height()) {
            return;
        }
        GridPosition candidate = new GridPosition(x, y);
        if (!snapshot.isTraversable(candidate)) {
            return;
        }
        int candidateId = candidate.cellId(snapshot.width());
        int tentativeG = Math.addExact(current.g(), 1);
        if (tentativeG >= bestG[candidateId]) {
            return;
        }
        bestG[candidateId] = tentativeG;
        parent[candidateId] = current.cellId();
        open.add(new Node(candidateId, tentativeG, heuristic(candidate, snapshot)));
    }

    private static int heuristic(GridPosition position, WorldSnapshot snapshot) {
        int best = UNREACHED;
        for (GridPosition exit : snapshot.exits()) {
            int distance = Math.addExact(Math.abs(position.x() - exit.x()),
                    Math.abs(position.y() - exit.y()));
            best = Math.min(best, distance);
        }
        return best;
    }

    private static GridPosition firstStep(int goalId, int startId, int[] parent, int width) {
        int step = goalId;
        while (parent[step] != startId) {
            step = parent[step];
            if (step < 0) {
                throw new IllegalStateException("A* parent chain did not reach its start");
            }
        }
        return position(step, width);
    }

    private static GridPosition position(int cellId, int width) {
        return new GridPosition(cellId % width, cellId / width);
    }
}
