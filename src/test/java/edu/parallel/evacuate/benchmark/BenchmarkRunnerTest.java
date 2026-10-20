package edu.parallel.evacuate.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class BenchmarkRunnerTest {
    @TempDir Path temporary;

    @Test void runsWarmupsAndMeasurementsHeadlesslyWithMatchingFingerprints() throws Exception {
        var config = new ScenarioConfig(8, 8, 12, 2, .1, 9876, OptionalInt.of(3), 100);
        var options = new BenchmarkOptions(temporary.resolve("nested/results.csv"), "unit", config, 1, 2, List.of(1, 2));
        List<BenchmarkRow> rows = new BenchmarkRunner().run(options);
        assertEquals(9, rows.size()); // 3 cases x (1 warmup + 2 measured)
        assertEquals(3, rows.stream().filter(BenchmarkRow::warmup).count());
        assertEquals(6, rows.stream().filter(row -> !row.warmup()).count());
        assertEquals(1, rows.stream().filter(row -> row.mode() == ExecutionMode.SEQUENTIAL).map(BenchmarkRow::workers).distinct().findFirst().orElseThrow());
        assertTrue(rows.stream().allMatch(row -> row.schemaVersion() == 1 && row.elapsedNanos() >= 0 && !row.finalFingerprint().isBlank()));

        new BenchmarkCsv().write(options.output(), rows);
        List<String> csv = Files.readAllLines(options.output());
        assertEquals(BenchmarkCsv.HEADER, csv.getFirst());
        assertEquals(rows.size() + 1, csv.size());
        assertTrue(csv.stream().skip(1).anyMatch(line -> line.contains(",true,")));
        assertTrue(csv.stream().skip(1).anyMatch(line -> line.contains(",false,")));
    }

    @Test void fingerprintValidatorRejectsMismatch() {
        BenchmarkRow first = row(ExecutionMode.SEQUENTIAL, 1, "a");
        BenchmarkRow second = row(ExecutionMode.PARALLEL, 2, "b");
        assertThrows(IllegalStateException.class,
                () -> BenchmarkRunner.validateComparableFingerprints(List.of(first, second)));
    }

    private static BenchmarkRow row(ExecutionMode mode, int workers, String fingerprint) {
        return new BenchmarkRow(1, Instant.EPOCH, "21", "vm", "os", "cpu", "same", 2, 2, 1,
                0, 1, 1, mode, workers, 1, false, 1, 0, 1, 1, 1, fingerprint);
    }
}
