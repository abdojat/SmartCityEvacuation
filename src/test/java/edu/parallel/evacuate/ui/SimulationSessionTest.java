package edu.parallel.evacuate.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SimulationSessionTest {
    @Test void bothModesRunOffCallerAndMarshalCallbacksToUiDispatcher() throws Exception {
        for (ExecutionMode mode : ExecutionMode.values()) verifyMode(mode);
    }

    @Test void pauseResumeAndStopAreNonBlockingLifecycleControls() throws Exception {
        try (TestUi ui = new TestUi(); SimulationSession session = new SimulationSession(ui::dispatch)) {
            CountDownLatch firstTick = new CountDownLatch(1);
            CountDownLatch stopped = new CountDownLatch(1);
            session.start(config(400), ExecutionMode.PARALLEL, 4, 10,
                    (snapshot, stats) -> firstTick.countDown(),
                    state -> { if (state == SimulationSession.State.STOPPED) stopped.countDown(); },
                    ignored -> { },
                    failure -> { throw new AssertionError(failure); });
            assertTrue(firstTick.await(5, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofMillis(250), session::pause);
            assertTrue(session.state() == SimulationSession.State.PAUSED);
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofMillis(250), session::resume);
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofMillis(250), session::stop);
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
        }
    }

    private static void verifyMode(ExecutionMode mode) throws Exception {
        try (TestUi ui = new TestUi(); SimulationSession session = new SimulationSession(ui::dispatch)) {
            CountDownLatch tick = new CountDownLatch(1);
            CopyOnWriteArrayList<String> callbackThreads = new CopyOnWriteArrayList<>();
            session.start(config(40), mode, 2, 0, (snapshot, stats) -> {
                callbackThreads.add(Thread.currentThread().getName()); tick.countDown();
            }, state -> callbackThreads.add(Thread.currentThread().getName()),
                    ignored -> callbackThreads.add(Thread.currentThread().getName()),
                    failure -> { throw new AssertionError(failure); });
            assertTrue(tick.await(5, TimeUnit.SECONDS));
            session.stop();
            ui.flush();
            assertTrue(callbackThreads.stream().allMatch(name -> name.equals("test-ui-thread")));
        }
    }

    @Test void completionTimeIsPublishedAndFormatted() throws Exception {
        try (TestUi ui = new TestUi(); SimulationSession session = new SimulationSession(ui::dispatch)) {
            CountDownLatch finished = new CountDownLatch(1);
            CopyOnWriteArrayList<Long> elapsed = new CopyOnWriteArrayList<>();
            session.start(config(1), ExecutionMode.SEQUENTIAL, 1, 0,
                    (snapshot, stats) -> { }, state -> { }, nanos -> {
                        elapsed.add(nanos);
                        finished.countDown();
                    }, failure -> { throw new AssertionError(failure); });
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(1, elapsed.size());
            assertTrue(elapsed.get(0) >= 0);
            assertEquals("1.250 s", EvacuationApplication.formatElapsed(1_250_000_000L));
            assertEquals("1 min 1.250 s", EvacuationApplication.formatElapsed(61_250_000_000L));
        }
    }

    private static ScenarioConfig config(int limit) {
        return new ScenarioConfig(25, 20, 100, 3, .1, 123, OptionalInt.of(10), limit);
    }

    private static final class TestUi implements AutoCloseable {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-ui-thread"));
        void dispatch(Runnable runnable) { executor.execute(runnable); }
        void flush() throws Exception { executor.submit(() -> {}).get(5, TimeUnit.SECONDS); }
        @Override public void close() { executor.shutdownNow(); }
    }
}
