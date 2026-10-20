package edu.parallel.evacuate.benchmark;

import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.SessionStats;
import edu.parallel.evacuate.api.Simulation;
import edu.parallel.evacuate.engine.parallel.ParallelSimulation;
import edu.parallel.evacuate.engine.sequential.SequentialSimulation;
import edu.parallel.evacuate.world.WorldGenerator;
import edu.parallel.evacuate.world.WorldState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs engine-only cases. World generation and CSV writing are outside measured time. */
public final class BenchmarkRunner {
    public List<BenchmarkRow> run(BenchmarkOptions options) {
        ArrayList<BenchmarkRow> rows = new ArrayList<>();
        int totalRounds = options.warmups() + options.repetitions();
        for (int round = 0; round < totalRounds; round++) {
            boolean warmup = round < options.warmups();
            int repetition = warmup ? round + 1 : round - options.warmups() + 1;
            List<Case> cases = cases(options.workers());
            if ((round & 1) == 1) Collections.reverse(cases); // counterbalance order bias
            for (Case benchmarkCase : cases) rows.add(runOne(options, benchmarkCase, repetition, warmup));
        }
        validateComparableFingerprints(rows);
        return List.copyOf(rows);
    }

    private BenchmarkRow runOne(BenchmarkOptions options, Case benchmarkCase, int repetition, boolean warmup) {
        WorldState initial = new WorldGenerator().generate(options.config());
        try (Simulation simulation = benchmarkCase.mode == ExecutionMode.SEQUENTIAL
                ? new SequentialSimulation(initial, options.config().tickLimit())
                : new ParallelSimulation(initial, benchmarkCase.workers, options.config().tickLimit())) {
            SessionStats stats = simulation.run();
            String cpu = System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", System.getProperty("os.arch", "unknown"));
            return new BenchmarkRow(BenchmarkRow.SCHEMA_VERSION, Instant.now(),
                    System.getProperty("java.version", "unknown"), System.getProperty("java.vm.name", "unknown"),
                    System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", ""), cpu,
                    options.scenario(), options.config().width(), options.config().height(), options.config().carCount(),
                    options.config().obstacleDensity(), options.config().exitCount(), options.config().seed(),
                    benchmarkCase.mode, benchmarkCase.workers, repetition, warmup, stats.ticksProcessed(),
                    stats.evacuated(), stats.remaining(), stats.elapsedNanos(), stats.ticksPerSecond(), stats.finalFingerprint());
        }
    }

    static void validateComparableFingerprints(List<BenchmarkRow> rows) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (BenchmarkRow row : rows) {
            String key = row.scenario() + '|' + row.width() + '|' + row.height() + '|' + row.cars() + '|'
                    + row.density() + '|' + row.exits() + '|' + row.seed() + '|' + row.warmup() + '|' + row.repetition();
            String previous = expected.putIfAbsent(key, row.finalFingerprint());
            if (previous != null && !previous.equals(row.finalFingerprint()))
                throw new IllegalStateException("correctness fingerprint mismatch for comparable case " + key);
        }
    }

    private static List<Case> cases(List<Integer> workers) {
        ArrayList<Case> result = new ArrayList<>();
        result.add(new Case(ExecutionMode.SEQUENTIAL, 1));
        for (int worker : workers) result.add(new Case(ExecutionMode.PARALLEL, worker));
        return result;
    }

    private record Case(ExecutionMode mode, int workers) { }
}
