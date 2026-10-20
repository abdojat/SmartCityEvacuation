package edu.parallel.evacuate.engine.parallel;

import edu.parallel.evacuate.engine.core.IntentComputer;
import edu.parallel.evacuate.engine.core.MoveIntent;
import edu.parallel.evacuate.engine.core.StayReason;
import edu.parallel.evacuate.pathfinding.PathFinder;
import edu.parallel.evacuate.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Computes deterministic indexed intents using stable contiguous partitions. */
public final class ParallelIntentComputer implements IntentComputer {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final AtomicInteger POOL_IDS = new AtomicInteger();

    private final PathFinder pathFinder;
    private final int workerCount;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean computing = new AtomicBoolean();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final Object futuresLock = new Object();
    private List<Future<?>> activeFutures = List.of();

    public ParallelIntentComputer(PathFinder pathFinder, int workerCount) {
        this.pathFinder = Objects.requireNonNull(pathFinder, "pathFinder");
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be positive");
        this.workerCount = workerCount;
        int poolId = POOL_IDS.incrementAndGet();
        AtomicInteger threadId = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable,
                    "evacuation-intent-" + poolId + "-" + threadId.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        });
    }

    @Override
    public List<MoveIntent> compute(WorldSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (closed.get()) throw new IllegalStateException("intent computer is closed");
        if (!computing.compareAndSet(false, true)) {
            throw new IllegalStateException("concurrent intent computation is not supported");
        }

        MoveIntent[] results = new MoveIntent[snapshot.cars().size()];
        List<Range> ranges = partitions(results.length, workerCount);
        ArrayList<Future<?>> futures = new ArrayList<>(ranges.size());
        try {
            for (Range range : ranges) {
                futures.add(executor.submit(() -> computeRange(snapshot, results, range)));
            }
            synchronized (futuresLock) {
                activeFutures = List.copyOf(futures);
                if (closed.get() || cancelRequested.get()) activeFutures.forEach(future -> future.cancel(true));
            }
            for (Future<?> future : futures) future.get();
            return List.copyOf(Arrays.asList(results));
        } catch (InterruptedException exception) {
            cancel(futures);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("parallel intent computation interrupted", exception);
        } catch (ExecutionException exception) {
            cancel(futures);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("parallel intent computation failed", cause);
        } catch (CancellationException | RejectedExecutionException exception) {
            cancel(futures);
            throw new IllegalStateException("parallel intent computation cancelled", exception);
        } finally {
            synchronized (futuresLock) {
                activeFutures = List.of();
            }
            computing.set(false);
        }
    }

    private void computeRange(WorldSnapshot snapshot, MoveIntent[] results, Range range) {
        for (int index = range.fromInclusive(); index < range.toExclusive(); index++) {
            if (Thread.currentThread().isInterrupted() || cancelRequested.get()) throw new CancellationException();
            var car = snapshot.cars().get(index);
            results[index] = pathFinder.nextStep(snapshot, car)
                    .map(target -> MoveIntent.move(car.id(), target))
                    .orElseGet(() -> MoveIntent.stay(car.id(),
                            snapshot.exits().contains(car.position())
                                    ? StayReason.ALREADY_AT_EXIT : StayReason.UNREACHABLE));
        }
    }

    static List<Range> partitions(int itemCount, int workerCount) {
        if (itemCount < 0 || workerCount <= 0) throw new IllegalArgumentException();
        int count = Math.min(itemCount, workerCount);
        ArrayList<Range> ranges = new ArrayList<>(count);
        for (int partition = 0; partition < count; partition++) {
            int from = partition * itemCount / count;
            int to = (partition + 1) * itemCount / count;
            ranges.add(new Range(from, to));
        }
        return List.copyOf(ranges);
    }

    @Override
    public void cancelActive() {
        cancelRequested.set(true);
        synchronized (futuresLock) {
            activeFutures.forEach(future -> future.cancel(true));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cancelActive();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("parallel intent executor did not terminate");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void cancel(List<Future<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    record Range(int fromInclusive, int toExclusive) { }
}
