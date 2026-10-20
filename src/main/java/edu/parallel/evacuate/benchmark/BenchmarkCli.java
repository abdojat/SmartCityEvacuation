package edu.parallel.evacuate.benchmark;

import java.util.List;

public final class BenchmarkCli {
    private BenchmarkCli() { }

    public static void main(String[] args) {
        try {
            BenchmarkOptions options = BenchmarkOptions.parse(args);
            List<BenchmarkRow> rows = new BenchmarkRunner().run(options);
            new BenchmarkCsv().write(options.output(), rows);
            long measured = rows.stream().filter(row -> !row.warmup()).count();
            System.out.printf("Wrote %d rows (%d measured) to %s%n", rows.size(), measured, options.output().toAbsolutePath());
        } catch (BenchmarkOptions.HelpRequested requested) {
            usage();
        } catch (Exception exception) {
            System.err.println("Benchmark failed: " + exception.getMessage());
            System.exit(2);
        }
    }

    private static void usage() {
        System.out.println("Usage: BenchmarkCli [--output file] [--scenario name] [--width n] [--height n] [--cars n]");
        System.out.println("  [--density d] [--exits n] [--seed n] [--closure-tick n|none] [--tick-limit n]");
        System.out.println("  [--warmups n] [--repetitions n] [--workers 1,2,4,8,16]");
    }
}
