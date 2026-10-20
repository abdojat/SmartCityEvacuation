package edu.parallel.evacuate.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.ScenarioConfig;
import edu.parallel.evacuate.api.Simulation;
import edu.parallel.evacuate.engine.parallel.ParallelSimulation;
import edu.parallel.evacuate.engine.sequential.SequentialSimulation;
import edu.parallel.evacuate.world.CarState;
import edu.parallel.evacuate.world.WorldGenerator;
import edu.parallel.evacuate.world.WorldState;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CrossModeRobustnessTest {
    private static final List<Integer> REQUIRED_WORKERS = List.of(1, 2, 4, 8, 16);
    private static final List<ScenarioConfig> DETERMINISM_MATRIX = List.of(
            new ScenarioConfig(9, 7, 18, 2, 0.00, 7L, OptionalInt.empty(), 40),
            new ScenarioConfig(15, 11, 52, 3, 0.17, 8675309L, OptionalInt.of(3), 70),
            new ScenarioConfig(24, 18, 140, 5, 0.28, -44119L, OptionalInt.of(8), 90));

    @Test
    void documentedSeedMatrixMatchesEveryCommittedTickAndSemanticStatistic() {
        WorldGenerator generator = new WorldGenerator();
        for (ScenarioConfig config : DETERMINISM_MATRIX) {
            WorldState initial = generator.generate(config);
            for (int workers : REQUIRED_WORKERS) {
                for (int repetition = 0; repetition < 2; repetition++) {
                    try (SequentialSimulation sequential = new SequentialSimulation(initial, config.tickLimit());
                         ParallelSimulation parallel = new ParallelSimulation(initial, workers, config.tickLimit())) {
                        assertEquivalent(sequential, parallel, config, workers, repetition);
                    }
                }
            }
        }
    }

    @Test
    void generatedLoadPreservesCollisionAndConservationInvariants() {
        ScenarioConfig config = new ScenarioConfig(32, 24, 300, 7, 0.22,
                20261020L, OptionalInt.of(5), 120);
        WorldState initial = new WorldGenerator().generate(config);
        for (int workers : REQUIRED_WORKERS) {
            try (ParallelSimulation simulation = new ParallelSimulation(initial, workers, config.tickLimit())) {
                int previousRemaining = config.carCount();
                while (simulation.snapshot().tick() < config.tickLimit()
                        && !simulation.snapshot().cars().isEmpty()) {
                    var stats = simulation.tick();
                    var snapshot = simulation.snapshot();
                    assertEquals(snapshot.tick(), stats.tick());
                    assertEquals(snapshot.cars().size(), stats.remaining());
                    assertEquals(config.carCount(), stats.remaining() + stats.evacuated());
                    assertTrue(stats.remaining() <= previousRemaining);
                    previousRemaining = stats.remaining();

                    Set<GridPosition> occupied = new HashSet<>();
                    snapshot.cars().forEach(car -> {
                        assertTrue(car.position().x() >= 0 && car.position().x() < snapshot.width());
                        assertTrue(car.position().y() >= 0 && car.position().y() < snapshot.height());
                        assertFalse(snapshot.blocked().contains(car.position()));
                        assertFalse(snapshot.exits().contains(car.position()));
                        assertTrue(occupied.add(car.position()), "collision at " + car.position());
                    });
                    if (stats.moved() == 0) break;
                }
            }
        }
    }

    @Test
    void cancelDuringActiveParallelTickReturnsPromptlyInterruptsWorkAndDoesNotCommit() throws Exception {
        BlockingFixture fixture = new BlockingFixture();
        ParallelSimulation simulation = fixture.simulation();
        CompletableFuture<?> tick = CompletableFuture.runAsync(simulation::tick);
        CompletableFuture<?> cancellation = null;
        try {
            assertTrue(fixture.entered.await(2, TimeUnit.SECONDS), "parallel worker did not start");
            cancellation = CompletableFuture.runAsync(simulation::cancel);
            assertTrue(completesWithin(cancellation, Duration.ofSeconds(1)),
                    "cancel() blocked behind the active tick instead of signalling worker cancellation");
            assertTrue(simulation.isCancelled());
            assertThrows(Exception.class, () -> tick.get(2, TimeUnit.SECONDS));
            assertEquals(fixture.initial.fingerprint(), simulation.snapshot().fingerprint());
            assertEquals(0, simulation.snapshot().tick());
        } finally {
            fixture.release.countDown();
            if (cancellation != null) cancellation.get(3, TimeUnit.SECONDS);
            try { tick.get(3, TimeUnit.SECONDS); } catch (Exception ignored) { }
            simulation.close();
        }
    }

    @Test
    void closeDuringActiveParallelTickReturnsPromptlyInterruptsWorkAndLeavesNoWorkers() throws Exception {
        BlockingFixture fixture = new BlockingFixture();
        ParallelSimulation simulation = fixture.simulation();
        CompletableFuture<?> tick = CompletableFuture.runAsync(simulation::tick);
        CompletableFuture<?> closing = null;
        try {
            assertTrue(fixture.entered.await(2, TimeUnit.SECONDS), "parallel worker did not start");
            closing = CompletableFuture.runAsync(simulation::close);
            assertTrue(completesWithin(closing, Duration.ofSeconds(1)),
                    "close() blocked behind the active tick instead of interrupting worker tasks");
            assertThrows(Exception.class, () -> tick.get(2, TimeUnit.SECONDS));
        } finally {
            fixture.release.countDown();
            if (closing != null) closing.get(3, TimeUnit.SECONDS);
            try { tick.get(3, TimeUnit.SECONDS); } catch (Exception ignored) { }
            simulation.close();
        }
        awaitNoIntentThreads();
    }

    @Test
    void closeIsIdempotentAndRejectsAllFurtherEngineWork() {
        ParallelSimulation simulation = new ParallelSimulation(shortRoad(), 4, 10);
        simulation.close();
        simulation.close();
        assertTrue(simulation.isCancelled());
        assertThrows(IllegalStateException.class, simulation::tick);
        assertThrows(IllegalStateException.class, simulation::run);
    }

    private static void assertEquivalent(Simulation sequential, Simulation parallel,
                                         ScenarioConfig config, int workers, int repetition) {
        assertEquals(sequential.snapshot().fingerprint(), parallel.snapshot().fingerprint());
        while (sequential.snapshot().tick() < config.tickLimit()
                && !sequential.snapshot().cars().isEmpty()) {
            var sequentialStats = sequential.tick();
            var parallelStats = parallel.tick();
            String context = "seed=" + config.seed() + ", workers=" + workers
                    + ", repetition=" + repetition + ", tick=" + sequentialStats.tick();
            assertEquals(sequentialStats, parallelStats, context);
            assertEquals(sequential.snapshot().fingerprint(), parallel.snapshot().fingerprint(), context);
            assertEquals(sequential.snapshot().cars(), parallel.snapshot().cars(), context);
            assertEquals(sequential.snapshot().blocked(), parallel.snapshot().blocked(), context);
            if (sequentialStats.moved() == 0) break;
        }
    }

    private static boolean completesWithin(CompletableFuture<?> future, Duration duration) {
        try {
            future.get(duration.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException exception) {
            return false;
        } catch (Exception exception) {
            throw new AssertionError("lifecycle operation failed", exception);
        }
    }

    private static void awaitNoIntentThreads() throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (intentThreads().isEmpty()) return;
            Thread.sleep(25);
        }
        assertTrue(intentThreads().isEmpty(), "leaked intent threads: " + intentThreads());
    }

    private static List<String> intentThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith("evacuation-intent-"))
                .map(Thread::getName).sorted().toList();
    }

    private static WorldState shortRoad() {
        return new WorldState(3, 1, 0, List.of(new CarState(0, new GridPosition(0, 0), 0)),
                Set.of(), Set.of(new GridPosition(2, 0)), OptionalInt.empty(), false);
    }

    private static final class BlockingFixture {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final WorldState initial = shortRoad();

        ParallelSimulation simulation() {
            return new ParallelSimulation(initial, (snapshot, car) -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("worker interrupted", exception);
                }
                return Optional.of(new GridPosition(1, 0));
            }, 2, 10);
        }
    }
}
