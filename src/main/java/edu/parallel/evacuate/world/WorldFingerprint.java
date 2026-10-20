package edu.parallel.evacuate.world;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical state fingerprint; excludes timing and execution strategy. */
public final class WorldFingerprint {
    private WorldFingerprint() {}

    public static String compute(WorldState state) {
        StringBuilder canonical = new StringBuilder("world-v1\n")
                .append(state.width()).append(',').append(state.height()).append('\n');
        state.blocked().stream().mapToInt(p -> p.cellId(state.width())).sorted()
                .forEach(id -> canonical.append('b').append(id).append('\n'));
        state.exits().stream().mapToInt(p -> p.cellId(state.width())).sorted()
                .forEach(id -> canonical.append('e').append(id).append('\n'));
        canonical.append('t').append(state.tick()).append('\n')
                .append("closure=").append(state.closureTick().isPresent() ? state.closureTick().getAsInt() : "none")
                .append(',').append(state.closureApplied()).append('\n');
        state.cars().forEach(car -> canonical.append('c').append(car.id()).append(',')
                .append(car.position().cellId(state.width())).append(',').append(car.waitTicks()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
