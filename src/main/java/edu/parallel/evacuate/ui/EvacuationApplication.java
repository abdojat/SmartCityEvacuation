package edu.parallel.evacuate.ui;

import edu.parallel.evacuate.api.ExecutionMode;
import edu.parallel.evacuate.api.SimulationSnapshot;
import edu.parallel.evacuate.api.TickStats;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Interactive JavaFX front end. Engine creation and ticking always run off the FX thread. */
public final class EvacuationApplication extends Application {
    private final TextField width = field("40"), height = field("30"), cars = field("250");
    private final TextField exits = field("4"), density = field("0.18"), seed = field("42");
    private final TextField closure = field("20"), limit = field("10000"), workers = field("4");
    private final TextField delay = field("35");
    private final ComboBox<ExecutionMode> mode = new ComboBox<>();
    private final Button start = new Button("Start"), pause = new Button("Pause");
    private final Button stop = new Button("Stop"), reset = new Button("Reset");
    private final Label status = new Label("Ready"), metrics = new Label("Tick 0 · Remaining —");
    private final GridCanvas canvas = new GridCanvas();
    private SimulationSession session;

    @Override public void start(Stage stage) {
        session = new SimulationSession(Platform::runLater);
        mode.getItems().setAll(ExecutionMode.values());
        mode.setValue(ExecutionMode.SEQUENTIAL);
        configureActions();

        GridPane form = new GridPane();
        form.setHgap(8); form.setVgap(8);
        add(form, 0, "Width", width); add(form, 1, "Height", height);
        add(form, 2, "Cars", cars); add(form, 3, "Exits", exits);
        add(form, 4, "Obstacle density", density); add(form, 5, "Seed", seed);
        add(form, 6, "Closure tick (blank = off)", closure); add(form, 7, "Tick limit", limit);
        add(form, 8, "Mode", mode); add(form, 9, "Parallel workers", workers);
        add(form, 10, "Tick delay (ms)", delay);

        HBox controls = new HBox(8, start, pause, stop, reset);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox sidebar = new VBox(14, new Label("Scenario"), form, controls, new Label("Session"),
                status, metrics, legend());
        sidebar.setPadding(new Insets(16));
        sidebar.setPrefWidth(315);

        StackPane viewport = new StackPane(canvas);
        viewport.setPadding(new Insets(16));
        canvas.widthProperty().bind(viewport.widthProperty().subtract(32));
        canvas.heightProperty().bind(viewport.heightProperty().subtract(32));
        BorderPane root = new BorderPane(viewport);
        root.setLeft(sidebar);
        Scene scene = new Scene(root, 1100, 720);
        var css = getClass().getResource("/edu/parallel/evacuate/ui/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("Smart City Emergency Evacuation Simulator");
        stage.setScene(scene);
        stage.setMinWidth(820); stage.setMinHeight(560);
        stage.show();
        setActive(false);
    }

    private void configureActions() {
        start.setOnAction(ignored -> begin());
        pause.setOnAction(ignored -> {
            if (session.state() == SimulationSession.State.RUNNING) {
                session.pause(); pause.setText("Resume"); status.setText("Paused");
            } else if (session.state() == SimulationSession.State.PAUSED) {
                session.resume(); pause.setText("Pause"); status.setText("Running");
            }
        });
        stop.setOnAction(ignored -> { session.stop(); status.setText("Stopping…"); });
        reset.setOnAction(ignored -> {
            session.stop(); canvas.clear(); metrics.setText("Tick 0 · Remaining —");
            status.setText("Ready"); pause.setText("Pause"); setActive(false);
        });
    }

    private void begin() {
        try {
            UiConfiguration config = UiConfiguration.parse(width.getText(), height.getText(), cars.getText(),
                    exits.getText(), density.getText(), seed.getText(), closure.getText(), limit.getText(),
                    mode.getValue(), workers.getText(), delay.getText());
            setError(false);
            setActive(true);
            session.start(config.scenario(), config.mode(), config.workers(), config.delayMillis(),
                    this::render, this::stateChanged, this::failed);
        } catch (RuntimeException error) {
            showError(error.getMessage());
        }
    }

    private void render(SimulationSnapshot snapshot, TickStats stats) {
        requireFxThread();
        canvas.show(snapshot);
        metrics.setText("Tick " + stats.tick() + " · Remaining " + stats.remaining()
                + " · Evacuated " + stats.evacuated() + " · Moved " + stats.moved()
                + " · Max wait " + stats.maxWaitTicks());
    }

    private void stateChanged(SimulationSession.State next) {
        requireFxThread();
        status.setText(switch (next) {
            case RUNNING -> "Running"; case PAUSED -> "Paused"; case STOPPED -> "Stopped";
            case FINISHED -> "Finished"; case FAILED -> "Failed"; case IDLE -> "Ready";
        });
        if (next == SimulationSession.State.STOPPED || next == SimulationSession.State.FINISHED
                || next == SimulationSession.State.FAILED) setActive(false);
    }

    private void failed(Throwable failure) {
        requireFxThread();
        showError(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }

    private void showError(String message) { status.setText("Error: " + message); setError(true); setActive(false); }
    private void setError(boolean value) { status.getStyleClass().remove("error"); if (value) status.getStyleClass().add("error"); }
    private void setActive(boolean active) { start.setDisable(active); pause.setDisable(!active); stop.setDisable(!active); }
    private static void requireFxThread() { if (!Platform.isFxApplicationThread()) throw new IllegalStateException("UI update off JavaFX Application Thread"); }

    private static TextField field(String text) { TextField field = new TextField(text); field.setPrefColumnCount(8); return field; }
    private static void add(GridPane pane, int row, String name, javafx.scene.Node control) {
        pane.add(new Label(name), 0, row); pane.add(control, 1, row); GridPane.setHgrow(control, Priority.ALWAYS);
    }
    private static HBox legend() {
        Label text = new Label("● Cars    ■ Exits    ■ Blocked"); text.getStyleClass().add("legend");
        return new HBox(text);
    }

    @Override public void stop() { if (session != null) session.close(); }
    public static void main(String[] args) { launch(args); }
}
