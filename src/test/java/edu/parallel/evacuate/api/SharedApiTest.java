package edu.parallel.evacuate.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SharedApiTest {
    @Test void exactlyTwoModes() { assertArrayEquals(new ExecutionMode[]{ExecutionMode.SEQUENTIAL, ExecutionMode.PARALLEL}, ExecutionMode.values()); }
    @Test void validatesConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ScenarioConfig(0,1,0,1,0,1,OptionalInt.empty(),1));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioConfig(2,2,0,1,1,1,OptionalInt.empty(),1));
        assertDoesNotThrow(() -> ScenarioConfig.defaults(7));
    }
    @Test void snapshotDefensivelyCopiesCollections() {
        var cars=new ArrayList<CarView>(); var blocked=new HashSet<GridPosition>();
        var snapshot=new SimulationSnapshot(2,2,0,cars,blocked,Set.of(new GridPosition(1,1)),"hash");
        cars.add(new CarView(1,new GridPosition(0,0),0)); blocked.add(new GridPosition(0,1));
        assertTrue(snapshot.cars().isEmpty()); assertTrue(snapshot.blocked().isEmpty());
    }
}
