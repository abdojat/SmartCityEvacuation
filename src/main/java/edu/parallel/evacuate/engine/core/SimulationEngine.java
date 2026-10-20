package edu.parallel.evacuate.engine.core;

import edu.parallel.evacuate.api.SessionStats;
import edu.parallel.evacuate.api.Simulation;
import edu.parallel.evacuate.api.SimulationSnapshot;
import edu.parallel.evacuate.api.TickStats;
import edu.parallel.evacuate.world.WorldState;
import java.util.Objects;

/** Atomic engine lifecycle shared by both intent-computation strategies. */
public class SimulationEngine implements Simulation {
    private final IntentComputer intentComputer;
    private final IntentResolver resolver;
    private final int tickLimit;
    private final int initialCarCount;
    private WorldState state;
    private volatile boolean paused;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private long elapsedNanos;

    public SimulationEngine(WorldState initialState, IntentComputer intentComputer, int tickLimit) {
        this(initialState, intentComputer, new IntentResolver(), tickLimit);
    }

    public SimulationEngine(WorldState initialState, IntentComputer intentComputer,
                            IntentResolver resolver, int tickLimit) {
        this.state = Objects.requireNonNull(initialState, "initialState");
        this.intentComputer = Objects.requireNonNull(intentComputer, "intentComputer");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        if (tickLimit <= 0) throw new IllegalArgumentException("tickLimit must be positive");
        this.tickLimit = tickLimit;
        this.initialCarCount = initialState.cars().size();
    }

    @Override
    public synchronized TickStats tick() {
        ensureOpen();
        if (paused) throw new IllegalStateException("simulation is paused");
        if (cancelled) throw new IllegalStateException("simulation is cancelled");
        if (finished()) return currentStats();

        long started = System.nanoTime();
        WorldState tickStart = state.applyScheduledClosure().state();
        var snapshot = tickStart.snapshot();
        var intents = intentComputer.compute(snapshot);
        Resolution resolution = resolver.resolve(tickStart, snapshot, intents, evacuatedCount());
        state = resolution.state();
        elapsedNanos = Math.addExact(elapsedNanos, System.nanoTime() - started);
        return resolution.stats();
    }

    @Override
    public synchronized SessionStats run() {
        ensureOpen();
        while (!paused && !cancelled && !finished()) {
            TickStats stats = tick();
            if (stats.moved() == 0) break;
        }
        return sessionStats();
    }

    @Override public synchronized void pause() { ensureOpen(); paused = true; }
    @Override public synchronized void resume() { ensureOpen(); if (!cancelled) paused = false; }
    @Override public void cancel() {
        if (!closed) {
            cancelled = true;
            intentComputer.cancelActive();
        }
    }
    @Override public synchronized SimulationSnapshot snapshot() { return state.publicSnapshot(); }
    @Override public synchronized boolean isPaused() { return paused; }
    @Override public synchronized boolean isCancelled() { return cancelled; }

    @Override
    public void close() {
        cancel();
        synchronized (this) {
            if (closed) return;
            closed = true;
            intentComputer.close();
        }
    }

    private boolean finished() { return state.cars().isEmpty() || state.tick() >= tickLimit; }
    private int evacuatedCount() { return initialCarCount - state.cars().size(); }
    private TickStats currentStats() {
        int maxWait = state.cars().stream().mapToInt(car -> car.waitTicks()).max().orElse(0);
        return new TickStats(state.tick(), state.cars().size(), evacuatedCount(), 0, 0, maxWait);
    }
    private SessionStats sessionStats() {
        return new SessionStats(elapsedNanos, state.tick(), evacuatedCount(), state.cars().size(), state.fingerprint());
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("simulation is closed"); }
}
