package edu.parallel.evacuate.benchmark;

import edu.parallel.evacuate.api.ScenarioConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Strict command-line options for a reproducible benchmark scenario. */
public record BenchmarkOptions(Path output, String scenario, ScenarioConfig config,
                               int warmups, int repetitions, List<Integer> workers) {
    public BenchmarkOptions {
        if (output == null) throw new NullPointerException("output");
        if (scenario == null || scenario.isBlank()) throw new IllegalArgumentException("scenario must not be blank");
        if (config == null) throw new NullPointerException("config");
        if (warmups < 0 || repetitions <= 0) throw new IllegalArgumentException("warmups must be nonnegative and repetitions positive");
        workers = List.copyOf(workers);
        if (workers.isEmpty() || workers.stream().anyMatch(value -> value == null || value <= 0))
            throw new IllegalArgumentException("workers must contain positive integers");
        if (workers.stream().distinct().count() != workers.size())
            throw new IllegalArgumentException("workers must not contain duplicates");
    }

    public static BenchmarkOptions parse(String[] args) {
        Path output = Path.of("benchmarks", "results.csv");
        String scenario = "custom";
        int width = 40, height = 30, cars = 250, exits = 4, tickLimit = 10_000;
        double density = .18;
        long seed = 1;
        OptionalInt closure = OptionalInt.of(20);
        int warmups = 2, repetitions = 5;
        List<Integer> workers = List.of(1, 2, 4, 8, 16);
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if ("--help".equals(key)) throw new HelpRequested();
            if (!key.startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("expected --name value, got: " + key);
            String value = args[++i];
            try {
                switch (key) {
                    case "--output" -> output = Path.of(value);
                    case "--scenario" -> scenario = value;
                    case "--width" -> width = Integer.parseInt(value);
                    case "--height" -> height = Integer.parseInt(value);
                    case "--cars" -> cars = Integer.parseInt(value);
                    case "--exits" -> exits = Integer.parseInt(value);
                    case "--density" -> density = Double.parseDouble(value);
                    case "--seed" -> seed = Long.parseLong(value);
                    case "--closure-tick" -> closure = "none".equalsIgnoreCase(value) ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value));
                    case "--tick-limit" -> tickLimit = Integer.parseInt(value);
                    case "--warmups" -> warmups = Integer.parseInt(value);
                    case "--repetitions" -> repetitions = Integer.parseInt(value);
                    case "--workers" -> workers = parseWorkers(value);
                    default -> throw new IllegalArgumentException("unknown option: " + key);
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid numeric value for " + key + ": " + value, exception);
            }
        }
        return new BenchmarkOptions(output, scenario,
                new ScenarioConfig(width, height, cars, exits, density, seed, closure, tickLimit),
                warmups, repetitions, workers);
    }

    private static List<Integer> parseWorkers(String text) {
        ArrayList<Integer> result = new ArrayList<>();
        for (String part : text.split(",", -1)) result.add(Integer.parseInt(part.trim()));
        return result;
    }

    static final class HelpRequested extends RuntimeException { }
}
