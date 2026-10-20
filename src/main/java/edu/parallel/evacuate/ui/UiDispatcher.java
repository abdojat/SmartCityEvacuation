package edu.parallel.evacuate.ui;

@FunctionalInterface
interface UiDispatcher {
    void dispatch(Runnable action);
}
