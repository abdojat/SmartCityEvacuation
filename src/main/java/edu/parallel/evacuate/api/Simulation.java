package edu.parallel.evacuate.api;

public interface Simulation extends AutoCloseable {
    TickStats tick();
    SessionStats run();
    void pause();
    void resume();
    void cancel();
    SimulationSnapshot snapshot();
    boolean isPaused();
    boolean isCancelled();
    @Override void close();
}
