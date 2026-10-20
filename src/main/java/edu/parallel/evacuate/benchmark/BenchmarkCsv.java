package edu.parallel.evacuate.benchmark;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BenchmarkCsv {
    public static final String HEADER = "schema_version,timestamp,java_version,jvm,os,cpu,scenario,width,height,cars,density,exits,seed,mode,workers,repetition,warmup,ticks,evacuated,remaining,elapsed_nanos,ticks_per_second,final_fingerprint";

    public void write(Path output, List<BenchmarkRow> rows) throws IOException {
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(HEADER); writer.newLine();
            for (BenchmarkRow row : rows) {
                writer.write(line(row)); writer.newLine();
            }
        }
    }

    String line(BenchmarkRow r) {
        return String.join(",", Integer.toString(r.schemaVersion()), csv(r.timestamp().toString()),
                csv(r.javaVersion()), csv(r.jvm()), csv(r.os()), csv(r.cpu()), csv(r.scenario()),
                Integer.toString(r.width()), Integer.toString(r.height()), Integer.toString(r.cars()),
                Double.toString(r.density()), Integer.toString(r.exits()), Long.toString(r.seed()), r.mode().name(),
                Integer.toString(r.workers()), Integer.toString(r.repetition()), Boolean.toString(r.warmup()),
                Long.toString(r.ticks()), Integer.toString(r.evacuated()), Integer.toString(r.remaining()),
                Long.toString(r.elapsedNanos()), String.format(Locale.ROOT, "%.6f", r.ticksPerSecond()),
                csv(r.finalFingerprint()));
    }

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
