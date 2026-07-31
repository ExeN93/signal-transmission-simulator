package pl.zwirko.signals.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    void samplesTheFunctionOnTheTimeGrid() {
        Signal signal = Signal.of(1, 4, t -> t);

        assertEquals(4, signal.length());
        assertArrayEquals(new double[] {0, 0.25, 0.5, 0.75}, signal.samples(), TOLERANCE);
    }

    @Test
    void durationFollowsFromSampleCountAndRate() {
        Signal signal = Signal.of(2, 50, t -> 0);

        assertEquals(100, signal.length());
        assertEquals(2, signal.duration(), TOLERANCE);
        assertEquals(0.02, signal.timeAt(1), TOLERANCE);
    }

    @Test
    @DisplayName("the sample array is copied in and out, so the record stays immutable")
    void doesNotShareItsSampleArray() {
        double[] source = {1, 2, 3};
        Signal signal = new Signal(source, 10);

        source[0] = 99;
        signal.samples()[1] = 99;

        assertArrayEquals(new double[] {1, 2, 3}, signal.samples(), TOLERANCE);
    }

    @Test
    void addsAndSubtractsSampleWise() {
        Signal a = new Signal(new double[] {1, 2, 3}, 10);
        Signal b = new Signal(new double[] {0.5, 0.5, 0.5}, 10);

        assertArrayEquals(new double[] {1.5, 2.5, 3.5}, a.plus(b).samples(), TOLERANCE);
        assertArrayEquals(new double[] {0.5, 1.5, 2.5}, a.minus(b).samples(), TOLERANCE);
    }

    @Test
    void refusesToCombineSignalsWithDifferentSamplingRates() {
        Signal a = new Signal(new double[] {1, 2}, 10);
        Signal b = new Signal(new double[] {1, 2}, 20);

        assertThrows(IllegalArgumentException.class, () -> a.plus(b));
    }

    @Test
    void refusesToCombineSignalsOfDifferentLengths() {
        Signal a = new Signal(new double[] {1, 2}, 10);
        Signal b = new Signal(new double[] {1, 2, 3}, 10);

        assertThrows(IllegalArgumentException.class, () -> a.plus(b));
    }

    @Test
    void scalingMultipliesEverySample() {
        Signal signal = new Signal(new double[] {1, -2, 3}, 10);

        assertArrayEquals(new double[] {2, -4, 6}, signal.scaled(2).samples(), TOLERANCE);
        assertEquals(3, signal.max(), TOLERANCE);
        assertEquals(-2, signal.min(), TOLERANCE);
    }

    @Test
    void rejectsANonPositiveSamplingRate() {
        assertThrows(IllegalArgumentException.class, () -> new Signal(new double[] {1}, 0));
    }
}
