package edu.parallel.evacuate.engine.core;

/** Why a car did not request movement during the intent stage. */
public enum StayReason {
    UNREACHABLE,
    ALREADY_AT_EXIT
}
