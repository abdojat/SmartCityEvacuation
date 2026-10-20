package edu.parallel.evacuate.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.parallel.evacuate.api.ExecutionMode;
import org.junit.jupiter.api.Test;

class UiConfigurationTest {
    @Test void parsesCompleteConfiguration() {
        UiConfiguration result = UiConfiguration.parse("20", "15", "40", "3", ".10", "7",
                "20", "500", ExecutionMode.PARALLEL, "8", "10");
        assertEquals(20, result.scenario().width());
        assertEquals(ExecutionMode.PARALLEL, result.mode());
        assertEquals(8, result.workers());
    }

    @Test void blankClosureDisablesEvent() {
        UiConfiguration result = UiConfiguration.parse("8", "8", "2", "1", "0", "1",
                " ", "20", ExecutionMode.SEQUENTIAL, "1", "0");
        assertEquals(false, result.scenario().closureTick().isPresent());
    }

    @Test void rejectsMalformedAndUnsafeValuesWithVisibleFriendlyMessage() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> UiConfiguration.parse("wide", "8", "2", "1", "0", "1", "", "20",
                        ExecutionMode.SEQUENTIAL, "1", "0"));
        assertEquals("configuration values must be valid numbers", malformed.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> UiConfiguration.parse("8", "8", "2", "1", "0", "1", "", "20",
                        ExecutionMode.SEQUENTIAL, "0", "0"));
    }
}
