package edu.parallel.evacuate.ui;

import edu.parallel.evacuate.api.CarView;
import edu.parallel.evacuate.api.GridPosition;
import edu.parallel.evacuate.api.SimulationSnapshot;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

final class GridCanvas extends Canvas {
    private SimulationSnapshot snapshot;

    GridCanvas() {
        widthProperty().addListener(ignored -> draw());
        heightProperty().addListener(ignored -> draw());
    }

    void show(SimulationSnapshot value) {
        snapshot = value;
        draw();
    }

    void clear() {
        snapshot = null;
        draw();
    }

    private void draw() {
        GraphicsContext graphics = getGraphicsContext2D();
        graphics.setFill(Color.web("#f4f7fb"));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        if (snapshot == null) return;
        double cell = Math.min(getWidth() / snapshot.width(), getHeight() / snapshot.height());
        double ox = (getWidth() - cell * snapshot.width()) / 2;
        double oy = (getHeight() - cell * snapshot.height()) / 2;
        graphics.setFill(Color.web("#334155"));
        snapshot.blocked().forEach(p -> fill(graphics, p, cell, ox, oy));
        graphics.setFill(Color.web("#16a34a"));
        snapshot.exits().forEach(p -> fill(graphics, p, cell, ox, oy));
        graphics.setFill(Color.web("#2563eb"));
        for (CarView car : snapshot.cars()) fill(graphics, car.position(), cell * .72,
                ox + cell * .14, oy + cell * .14, cell);
    }

    private static void fill(GraphicsContext g, GridPosition p, double cell, double ox, double oy) {
        g.fillRect(ox + p.x() * cell, oy + p.y() * cell, Math.max(1, cell - .5), Math.max(1, cell - .5));
    }

    private static void fill(GraphicsContext g, GridPosition p, double size,
                             double ox, double oy, double cell) {
        g.fillOval(ox + p.x() * cell, oy + p.y() * cell, Math.max(1, size), Math.max(1, size));
    }
}
