package pl.zwirko.signals.transmission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BerAnalyzerTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    @DisplayName("a channel that changed nothing has an error rate of zero")
    void noErrorsMeansZero() {
        int[] sent = {1, 0, 1, 1, 0, 0, 1, 0};

        assertEquals(0.0, BerAnalyzer.bitErrorRate(sent, sent.clone()), TOLERANCE);
    }

    @Test
    @DisplayName("a fully inverted stream has an error rate of one")
    void everyBitWrongMeansOne() {
        int[] sent = {1, 0, 1, 1};
        int[] received = {0, 1, 0, 0};

        assertEquals(1.0, BerAnalyzer.bitErrorRate(sent, received), TOLERANCE);
    }

    @Test
    void countsTheProportionOfWrongBits() {
        int[] sent = {1, 0, 1, 0};
        int[] received = {1, 1, 0, 0};

        assertEquals(0.5, BerAnalyzer.bitErrorRate(sent, received), TOLERANCE);
    }

    @Test
    @DisplayName("padding added by a block code does not dilute the error rate")
    void comparesOverThePayloadLength() {
        int[] sent = {1, 0, 1, 0};
        int[] receivedWithPadding = {1, 1, 1, 0, 0, 0, 0, 0};

        assertEquals(0.25, BerAnalyzer.bitErrorRate(sent, receivedWithPadding), TOLERANCE);
    }

    @Test
    void anEmptyPayloadHasNoErrors() {
        assertEquals(0.0, BerAnalyzer.bitErrorRate(new int[0], new int[0]), TOLERANCE);
    }

    @Test
    void refusesToScoreAStreamShorterThanWhatWasSent() {
        assertThrows(IllegalArgumentException.class,
                () -> BerAnalyzer.bitErrorRate(new int[] {1, 0, 1}, new int[] {1, 0}));
    }

    @Test
    void linearRangeSpansBothEndpoints() {
        assertArrayEquals(new double[] {0, 0.5, 1.0},
                BerAnalyzer.linearRange(0, 1, 3), TOLERANCE);
        assertArrayEquals(new double[] {2, 4, 6, 8},
                BerAnalyzer.linearRange(2, 8, 4), TOLERANCE);
    }

    @Test
    void linearRangeNeedsAtLeastTwoPoints() {
        assertThrows(IllegalArgumentException.class, () -> BerAnalyzer.linearRange(0, 1, 1));
    }
}
