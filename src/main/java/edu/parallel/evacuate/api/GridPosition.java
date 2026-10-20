package edu.parallel.evacuate.api;

public record GridPosition(int x, int y) {
    public GridPosition {
        if (x < 0 || y < 0) throw new IllegalArgumentException("coordinates must be nonnegative");
    }
    public int cellId(int width) {
        if (width <= 0 || x >= width) throw new IllegalArgumentException("position is outside width");
        return Math.addExact(Math.multiplyExact(y, width), x);
    }
    public int manhattanDistance(GridPosition other) {
        return Math.addExact(Math.abs(x - other.x), Math.abs(y - other.y));
    }
}
