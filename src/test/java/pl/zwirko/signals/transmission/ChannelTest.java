package pl.zwirko.signals.transmission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.zwirko.signals.core.Signal;
import pl.zwirko.signals.core.SignalGenerator;

class ChannelTest {

    private static final double TOLERANCE = 1e-12;

    private final Signal carrier = SignalGenerator.sine(1, 10, 0, 1, 100);

    @Test
    @DisplayName("a perfect channel delivers the signal untouched")
    void perfectChannelIsTheIdentity() {
        Signal received = Channel.PERFECT.transmit(carrier, new Random(1));

        assertTrue(Channel.PERFECT.isPerfect());
        assertArrayEquals(carrier.samples(), received.samples(), TOLERANCE);
    }

    @Test
    @DisplayName("the same seed gives the same noise, so a sweep can be replayed")
    void noiseIsReproducibleFromASeed() {
        Channel channel = Channel.PERFECT.withNoise(0.5);

        Signal first = channel.transmit(carrier, new Random(99));
        Signal second = channel.transmit(carrier, new Random(99));
        Signal different = channel.transmit(carrier, new Random(100));

        assertArrayEquals(first.samples(), second.samples(), TOLERANCE);
        assertFalse(java.util.Arrays.equals(first.samples(), different.samples()));
    }

    @Test
    void noiseStaysWithinTheAmplitudeItWasGiven() {
        Channel channel = Channel.PERFECT.withNoise(0.25);

        Signal received = channel.transmit(carrier, new Random(3));

        double[] clean = carrier.samples();
        double[] noisy = received.samples();
        for (int n = 0; n < clean.length; n++) {
            assertTrue(Math.abs(noisy[n] - clean[n]) <= 0.25 + TOLERANCE,
                    "noise exceeded its amplitude at sample " + n);
        }
    }

    @Test
    @DisplayName("decay is exponential in time and leaves the first sample alone")
    void decayFollowsAnExponentialEnvelope() {
        Channel channel = Channel.PERFECT.withDecay(2);

        assertEquals(1.0, channel.envelopeAt(0), TOLERANCE);
        assertEquals(Math.exp(-2), channel.envelopeAt(1), TOLERANCE);
        assertEquals(Math.exp(-4), channel.envelopeAt(2), TOLERANCE);
    }

    @Test
    @DisplayName("suppression fades linearly and holds at silence afterwards")
    void suppressionFadesToSilence() {
        Channel channel = Channel.PERFECT.withSuppressionAt(4);

        assertEquals(1.0, channel.envelopeAt(0), TOLERANCE);
        assertEquals(0.5, channel.envelopeAt(2), TOLERANCE);
        assertEquals(0.0, channel.envelopeAt(4), TOLERANCE);
        assertEquals(0.0, channel.envelopeAt(6), TOLERANCE);
    }

    @Test
    @DisplayName("attenuation is applied before noise, so the noise floor stays put")
    void noiseIsNotAttenuatedWithTheSignal() {
        Signal silence = new Signal(new double[100], 100);
        // By the last sample the envelope is down to e^-9.9, about 5e-5. If the noise were
        // scaled by it too, nothing near the full noise amplitude could survive out there.
        Channel channel = Channel.PERFECT.withNoise(0.5).withDecay(10);

        Signal received = channel.transmit(silence, new Random(5));

        double loudestLateSample = 0;
        for (int n = 80; n < 100; n++) {
            loudestLateSample = Math.max(loudestLateSample, Math.abs(received.sample(n)));
        }
        assertTrue(loudestLateSample > 0.1,
                "expected undiminished noise late in the signal, got " + loudestLateSample);
    }

    @Test
    void withMethodsReturnANewChannelAndLeaveTheOriginalAlone() {
        Channel noisy = Channel.PERFECT.withNoise(1);

        assertTrue(Channel.PERFECT.isPerfect());
        assertEquals(1, noisy.noiseAmplitude());
        assertFalse(noisy.isPerfect());
    }

    @Test
    void rejectsNegativeParameters() {
        assertThrows(IllegalArgumentException.class, () -> Channel.PERFECT.withNoise(-1));
        assertThrows(IllegalArgumentException.class, () -> Channel.PERFECT.withDecay(-1));
        assertThrows(IllegalArgumentException.class, () -> Channel.PERFECT.withSuppressionAt(0));
    }
}
