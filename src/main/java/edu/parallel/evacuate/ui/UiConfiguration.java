package edu.parallel.evacuate.ui;

import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.ScenarioConfig;
import java.util.OptionalInt;

record UiConfiguration(ScenarioConfig scenario, ExecutionMode mode, int workers, long delayMillis) {
    static UiConfiguration parse(String width, String height, String cars, String exits,
                                 String density, String seed, String closureTick, String tickLimit,
                                 ExecutionMode mode, String workers, String delayMillis) {
        try {
            OptionalInt closure = closureTick == null || closureTick.isBlank()
                    ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(closureTick.trim()));
            ScenarioConfig scenario = new ScenarioConfig(Integer.parseInt(width.trim()),
                    Integer.parseInt(height.trim()), Integer.parseInt(cars.trim()),
                    Integer.parseInt(exits.trim()), Double.parseDouble(density.trim()),
                    Long.parseLong(seed.trim()), closure, Integer.parseInt(tickLimit.trim()));
            int parsedWorkers = Integer.parseInt(workers.trim());
            long parsedDelay = Long.parseLong(delayMillis.trim());
            if (mode == null) throw new IllegalArgumentException("select an execution mode");
            if (parsedWorkers <= 0) throw new IllegalArgumentException("workers must be positive");
            if (parsedDelay < 0) throw new IllegalArgumentException("tick delay must be nonnegative");
            return new UiConfiguration(scenario, mode, parsedWorkers, parsedDelay);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("configuration values must be valid numbers", error);
        }
    }
}
