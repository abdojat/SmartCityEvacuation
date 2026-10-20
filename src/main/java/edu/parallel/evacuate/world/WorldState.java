package edu.parallel.evacuate.world;

import edu.parallel.evacuate.api.CarView;
import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.SimulationSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Immutable committed world state. */
public final class WorldState {
    private final int width;
    private final int height;
    private final long tick;
    private final List<CarState> cars;
    private final Set<GridPosition> blocked;
    private final Set<GridPosition> exits;
    private final OptionalInt closureTick;
    private final boolean closureApplied;
    private final String fingerprint;

    public WorldState(int width, int height, long tick, List<CarState> cars,
                      Set<GridPosition> blocked, Set<GridPosition> exits,
                      OptionalInt closureTick, boolean closureApplied) {
        this.width = width;
        this.height = height;
        this.tick = tick;
        this.cars = List.copyOf(Objects.requireNonNull(cars, "cars").stream()
                .sorted(Comparator.comparingInt(CarState::id)).toList());
        this.blocked = Set.copyOf(Objects.requireNonNull(blocked, "blocked"));
        this.exits = Set.copyOf(Objects.requireNonNull(exits, "exits"));
        this.closureTick = Objects.requireNonNull(closureTick, "closureTick");
        this.closureApplied = closureApplied;
        WorldValidator.validate(width, height, tick, this.cars, this.blocked, this.exits,
                closureTick, closureApplied);
        this.fingerprint = WorldFingerprint.compute(this);
    }

    public int width() { return width; }
    public int height() { return height; }
    public long tick() { return tick; }
    public List<CarState> cars() { return cars; }
    public Set<GridPosition> blocked() { return blocked; }
    public Set<GridPosition> exits() { return exits; }
    public OptionalInt closureTick() { return closureTick; }
    public boolean closureApplied() { return closureApplied; }
    public String fingerprint() { return fingerprint; }

    public WorldSnapshot snapshot() {
        return new WorldSnapshot(width, height, tick, cars, blocked, exits,
                closureTick, closureApplied, fingerprint);
    }

    public SimulationSnapshot publicSnapshot() {
        List<CarView> views = cars.stream()
                .map(car -> new CarView(car.id(), car.position(), car.waitTicks())).toList();
        return new SimulationSnapshot(width, height, tick, views, blocked, exits, fingerprint);
    }

    /** Creates the next committed state; invariant checks happen before it is returned. */
    public WorldState next(List<CarState> nextCars) {
        return new WorldState(width, height, Math.addExact(tick, 1), nextCars, blocked, exits,
                closureTick, closureApplied);
    }

    /** Applies an eligible event, or deterministically defers it if no safe cell exists. */
    public ClosureResult applyScheduledClosure() {
        if (closureApplied || closureTick.isEmpty() || tick < closureTick.getAsInt() || cars.isEmpty()) {
            return new ClosureResult(this, Optional.empty());
        }
        Set<GridPosition> occupied = new HashSet<>();
        cars.forEach(car -> occupied.add(car.position()));
        for (int cellId = 0; cellId < Math.multiplyExact(width, height); cellId++) {
            GridPosition candidate = new GridPosition(cellId % width, cellId / width);
            if (blocked.contains(candidate) || exits.contains(candidate) || occupied.contains(candidate)) continue;
            HashSet<GridPosition> candidateBlocked = new HashSet<>(blocked);
            candidateBlocked.add(candidate);
            if (cars.stream().allMatch(car -> canReachExit(car.position(), candidateBlocked))) {
                WorldState result = new WorldState(width, height, tick, cars, candidateBlocked, exits,
                        closureTick, true);
                return new ClosureResult(result, Optional.of(candidate));
            }
        }
        return new ClosureResult(this, Optional.empty());
    }

    private boolean canReachExit(GridPosition start, Set<GridPosition> obstacles) {
        if (exits.contains(start)) return true;
        ArrayDeque<GridPosition> queue = new ArrayDeque<>();
        HashSet<GridPosition> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            GridPosition current = queue.removeFirst();
            for (GridPosition neighbor : neighbors(current)) {
                if (obstacles.contains(neighbor) || !seen.add(neighbor)) continue;
                if (exits.contains(neighbor)) return true;
                queue.addLast(neighbor);
            }
        }
        return false;
    }

    private List<GridPosition> neighbors(GridPosition position) {
        ArrayList<GridPosition> result = new ArrayList<>(4);
        if (position.y() > 0) result.add(new GridPosition(position.x(), position.y() - 1));
        if (position.x() + 1 < width) result.add(new GridPosition(position.x() + 1, position.y()));
        if (position.y() + 1 < height) result.add(new GridPosition(position.x(), position.y() + 1));
        if (position.x() > 0) result.add(new GridPosition(position.x() - 1, position.y()));
        return result;
    }
}
