package edu.parallel.evacuate.ui;

import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.ScenarioConfig;
import edu.parallel.evacuate.api.Simulation;
import edu.parallel.evacuate.api.SimulationSnapshot;
import edu.parallel.evacuate.api.TickStats;
import edu.parallel.evacuate.engine.parallel.ParallelSimulation;
import edu.parallel.evacuate.engine.sequential.SequentialSimulation;
import edu.parallel.evacuate.world.WorldGenerator;
import edu.parallel.evacuate.world.WorldState;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Owns one background simulation session; no JavaFX scene state is touched here. */
final class SimulationSession implements AutoCloseable {
    enum State { IDLE, RUNNING, PAUSED, STOPPED, FINISHED, FAILED }

    private final UiDispatcher ui;
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "evacuation-ui-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final Object pauseMonitor = new Object();
    private volatile State state = State.IDLE;
    private volatile Simulation simulation;
    private volatile Future<?> task;
    private volatile Consumer<State> stateConsumer = ignored -> { };

    SimulationSession(UiDispatcher ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    synchronized void start(ScenarioConfig config, ExecutionMode mode, int workers, long delayMillis,
                            BiConsumer<SimulationSnapshot, TickStats> onTick,
                            Consumer<State> onState, LongConsumer onCompletion,
                            Consumer<Throwable> onError) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mode, "mode");
        if (workers <= 0) throw new IllegalArgumentException("workers must be positive");
        if (delayMillis < 0) throw new IllegalArgumentException("delay must be nonnegative");
        if (task != null && !task.isDone()) throw new IllegalStateException("a simulation is already active");
        stopRequested.set(false);
        stateConsumer = Objects.requireNonNull(onState, "onState");
        Objects.requireNonNull(onCompletion, "onCompletion");
        state = State.RUNNING;
        publishState(onState, State.RUNNING);
        task = coordinator.submit(() -> runLoop(config, mode, workers, delayMillis,
                onTick, onState, onCompletion, onError));
    }

    private void runLoop(ScenarioConfig config, ExecutionMode mode, int workers, long delayMillis,
                         BiConsumer<SimulationSnapshot, TickStats> onTick,
                         Consumer<State> onState, LongConsumer onCompletion,
                         Consumer<Throwable> onError) {
        long startedNanos = System.nanoTime();
        try {
            WorldState world = new WorldGenerator().generate(config);
            simulation = mode == ExecutionMode.SEQUENTIAL
                    ? new SequentialSimulation(world, config.tickLimit())
                    : new ParallelSimulation(world, workers, config.tickLimit());
            publishTick(onTick, simulation.snapshot(), new TickStats(0, config.carCount(), 0, 0, 0, 0));
            while (!stopRequested.get()) {
                awaitResume();
                if (stopRequested.get()) break;
                TickStats stats = simulation.tick();
                publishTick(onTick, simulation.snapshot(), stats);
                if (stats.remaining() == 0 || stats.tick() >= config.tickLimit() || stats.moved() == 0) {
                    long elapsedNanos = System.nanoTime() - startedNanos;
                    state = State.FINISHED;
                    ui.dispatch(() -> onCompletion.accept(elapsedNanos));
                    publishState(onState, State.FINISHED);
                    return;
                }
                if (delayMillis > 0) Thread.sleep(delayMillis);
            }
            state = State.STOPPED;
            publishState(onState, State.STOPPED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state = State.STOPPED;
            publishState(onState, State.STOPPED);
        } catch (Throwable failure) {
            state = State.FAILED;
            ui.dispatch(() -> onError.accept(failure));
            publishState(onState, State.FAILED);
        } finally {
            Simulation current = simulation;
            simulation = null;
            if (current != null) current.close();
        }
    }

    synchronized void pause() {
        if (state != State.RUNNING) return;
        state = State.PAUSED;
        Simulation current = simulation;
        if (current != null) current.pause();
    }

    synchronized void resume() {
        if (state != State.PAUSED) return;
        Simulation current = simulation;
        if (current != null) current.resume();
        state = State.RUNNING;
        synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
    }

    synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPED || state == State.FINISHED || state == State.FAILED) return;
        stopRequested.set(true);
        state = State.STOPPED;
        publishState(stateConsumer, State.STOPPED);
        Simulation current = simulation;
        if (current != null) current.cancel();
        synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
        Future<?> currentTask = task;
        if (currentTask != null) currentTask.cancel(true);
    }

    State state() { return state; }

    private void awaitResume() throws InterruptedException {
        synchronized (pauseMonitor) {
            while (state == State.PAUSED && !stopRequested.get()) pauseMonitor.wait();
        }
    }

    private void publishTick(BiConsumer<SimulationSnapshot, TickStats> consumer,
                             SimulationSnapshot snapshot, TickStats stats) {
        ui.dispatch(() -> consumer.accept(snapshot, stats));
    }

    private void publishState(Consumer<State> consumer, State next) {
        ui.dispatch(() -> consumer.accept(next));
    }

    @Override public void close() {
        stop();
        coordinator.shutdownNow();
    }
}
