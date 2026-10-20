package edu.parallel.evacuate.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BenchmarkOptionsTest {
    @Test void parsesCompleteValidCommandLine() {
        BenchmarkOptions options = BenchmarkOptions.parse(new String[]{"--output", "target/out.csv", "--scenario", "smoke",
                "--width", "8", "--height", "7", "--cars", "10", "--exits", "2", "--density", ".1",
                "--seed", "42", "--closure-tick", "none", "--tick-limit", "80", "--warmups", "0",
                "--repetitions", "2", "--workers", "1,4"});
        assertEquals("smoke", options.scenario());
        assertEquals(42, options.config().seed());
        assertTrue(options.config().closureTick().isEmpty());
        assertEquals(java.util.List.of(1, 4), options.workers());
    }

    @Test void rejectsUnknownMissingAndInvalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> BenchmarkOptions.parse(new String[]{"--bogus", "1"}));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkOptions.parse(new String[]{"--width"}));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkOptions.parse(new String[]{"--workers", "1,0"}));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkOptions.parse(new String[]{"--repetitions", "0"}));
    }
}
