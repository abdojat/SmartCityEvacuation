package edu.parallel.evacuate.engine.parallel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.ScenarioConfig;
import edu.parallel.evacuate.engine.sequential.SequentialSimulation;
import edu.parallel.evacuate.pathfinding.AStarPathfinder;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldGenerator;
import edu.parallel.evacuate.world.WorldState;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ParallelIntentComputerTest {
    @Test
    void stablePartitionsAreContiguousBoundedAndComplete() {
        assertTrue(ParallelIntentComputer.partitions(0, 4).isEmpty());
        var ranges = ParallelIntentComputer.partitions(11, 4);
        assertEquals(4, ranges.size());
        assertEquals(List.of(new ParallelIntentComputer.Range(0, 2),
                        new ParallelIntentComputer.Range(2, 5),
                        new ParallelIntentComputer.Range(5, 8),
                        new ParallelIntentComputer.Range(8, 11)), ranges);
        assertEquals(3, ParallelIntentComputer.partitions(3, 16).size());
    }

    @Test
    void everyTickMatchesSequentialForRequiredWorkerCountsAndRepeatedRuns() {
        ScenarioConfig config = new ScenarioConfig(16, 12, 55, 3, 0.14,
                8675309L, OptionalInt.of(4), 80);
        WorldState initial = new WorldGenerator().generate(config);
        for (int workers : List.of(1, 2, 4, 8, 16)) {
            for (int repetition = 0; repetition < 2; repetition++) {
                try (SequentialSimulation sequential = new SequentialSimulation(initial, config.tickLimit());
                     ParallelSimulation parallel = new ParallelSimulation(initial, workers, config.tickLimit())) {
                    while (sequential.snapshot().tick() < config.tickLimit()
                            && !sequential.snapshot().cars().isEmpty()) {
                        var sequentialStats = sequential.tick();
                        var parallelStats = parallel.tick();
                        assertEquals(sequentialStats, parallelStats,
                                "semantic stats differ with " + workers + " workers");
                        assertEquals(sequential.snapshot().fingerprint(), parallel.snapshot().fingerprint(),
                                "fingerprint differs with " + workers + " workers");
                        if (sequentialStats.moved() == 0) break;
                    }
                }
            }
        }
    }

    @Test
    void executorIsReusedAcrossComputationsAndCloseRejectsWork() {
        Set<String> threadNames = java.util.Collections.synchronizedSet(new HashSet<>());
        var delegate = new AStarPathfinder();
        var computer = new ParallelIntentComputer((snapshot, car) -> {
            threadNames.add(Thread.currentThread().getName());
            return delegate.nextStep(snapshot, car);
        }, 2);
        var snapshot = roadWithCars(6).snapshot();
        computer.compute(snapshot);
        Set<String> firstNames = Set.copyOf(threadNames);
        computer.compute(snapshot);
        assertEquals(firstNames, threadNames);
        assertTrue(threadNames.size() <= 2);
        computer.close();
        computer.close();
        assertThrows(IllegalStateException.class, () -> computer.compute(snapshot));
    }

    @Test
    void workerFailureCancelsTickWithoutChangingWorld() {
        WorldState initial = roadWithCars(4);
        try (ParallelSimulation simulation = new ParallelSimulation(initial, (snapshot, car) -> {
            throw new IllegalArgumentException("injected");
        }, 4, 10)) {
            assertThrows(IllegalArgumentException.class, simulation::tick);
            assertEquals(initial.fingerprint(), simulation.snapshot().fingerprint());
            assertEquals(0, simulation.snapshot().tick());
        }
    }

    @Test
    void closeInterruptsOutstandingWorkersAndLeavesNoPoolThreads() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ParallelIntentComputer computer = new ParallelIntentComputer((snapshot, car) -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return java.util.Optional.empty();
        }, 2);
        CompletableFuture<Void> computation = CompletableFuture.runAsync(() -> computer.compute(roadWithCars(4).snapshot()));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), computer::close);
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertThrows(Exception.class, () -> computation.get(2, TimeUnit.SECONDS));
        awaitNoIntentThreads();
    }

    private static void awaitNoIntentThreads() throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive() && thread.getName().startsWith("evacuation-intent-"));
            if (!alive) return;
            Thread.sleep(25);
        }
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && thread.getName().startsWith("evacuation-intent-")));
    }

    private static WorldState roadWithCars(int count) {
        var cars = new java.util.ArrayList<CarState>();
        for (int id = 0; id < count; id++) cars.add(new CarState(id, new GridPosition(id, 0), 0));
        return new WorldState(count + 2, 1, 0, cars, Set.of(), Set.of(new GridPosition(count + 1, 0)),
                OptionalInt.empty(), false);
    }
}
