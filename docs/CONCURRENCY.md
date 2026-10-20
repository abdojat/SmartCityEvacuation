# Concurrency and determinism

Each tick has strict happens-before phases: coordinator applies an eligible event; creates one immutable snapshot; workers compute indexed intents; coordinator joins every task; coordinator resolves and atomically commits; then observers receive immutable data. No worker mutates world state or appends to shared collections.

Parallel mode owns one fixed executor reused across ticks. A tick submits no more than the configured worker count, using stable contiguous partitions of the car-ID-ordered list. Random choices are completed during deterministic generation from the configured seed; no shared `Random` exists during ticks.

Cancellation interrupts outstanding work where safe. An interruption or worker exception discards all intents and preserves the previous state. Shutdown rejects new ticks, cancels work, calls `shutdownNow` if bounded graceful termination expires, and preserves interrupt status. Locks are never held while awaiting futures. `close` is idempotent.

Road closure is attempted at its configured tick. Candidate road cells use ascending cell ID and must be traversable, non-exit, unoccupied, and leave every active car connected to an exit. If none qualifies, the event is deterministically deferred one tick and retried while cars remain.

JavaFX controllers run the engine on background services. Only immutable snapshots cross into the UI; all scene-graph changes occur on the Application Thread. Pause stops scheduling new ticks after any current atomic tick finishes.

Determinism evidence compares every committed fingerprint and semantic statistic across sequential and parallel modes at 1/2/4/8/16 workers. The occupied-at-start rule can cause traffic gridlock but eliminates swaps/cycles; this is not a thread deadlock.
