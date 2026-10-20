package edu.parallel.evacuate.engine.core;

import edu.parallel.evacuate.api.GridPosition;
import java.util.Objects;
import java.util.Optional;

/** Immutable movement request for exactly one car. */
public record MoveIntent(int carId, Optional<GridPosition> target, Optional<StayReason> stayReason) {
    public MoveIntent {
        if (carId < 0) throw new IllegalArgumentException("carId must be nonnegative");
        target = Objects.requireNonNull(target, "target");
        stayReason = Objects.requireNonNull(stayReason, "stayReason");
        if (target.isPresent() == stayReason.isPresent()) {
            throw new IllegalArgumentException("intent must contain exactly one target or stay reason");
        }
    }

    public static MoveIntent move(int carId, GridPosition target) {
        return new MoveIntent(carId, Optional.of(Objects.requireNonNull(target, "target")), Optional.empty());
    }

    public static MoveIntent stay(int carId, StayReason reason) {
        return new MoveIntent(carId, Optional.empty(), Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    public boolean isMove() { return target.isPresent(); }
}
