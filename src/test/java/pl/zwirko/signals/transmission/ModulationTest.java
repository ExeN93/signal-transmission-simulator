package pl.zwirko.signals.transmission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.zwirko.signals.core.Bits;
import pl.zwirko.signals.core.Signal;
import pl.zwirko.signals.core.Spectrum;

/**
 * Modulating and demodulating over a perfect channel has to be the identity. Everything else in
 * the simulator is measured against that baseline, so if it does not hold, no error rate
 * further down means anything.
 */
class ModulationTest {

    private static final double SAMPLING_RATE = 1000;
    private static final double BIT_DURATION = 0.1;
    private static final double CARRIER = 50;
    private static final double TONE_FOR_ZERO = 50;
    private static final double TONE_FOR_ONE = 80;
    private static final double LOW_AMPLITUDE = 0.5;
    private static final double HIGH_AMPLITUDE = 1.0;

    @ParameterizedTest(name = "bits {0}")
    @CsvSource({"1010", "1100", "0110", "1011001", "10", "0100001"})
    @DisplayName("ASK survives a noiseless channel")
    void askRoundTrip(String pattern) {
        int[] bits = parse(pattern);

        Signal modulated = Modulator.ask(bits, LOW_AMPLITUDE, HIGH_AMPLITUDE, CARRIER,
                BIT_DURATION, SAMPLING_RATE);

        assertArrayEquals(bits, Demodulator.ask(modulated, CARRIER, BIT_DURATION));
    }

    @ParameterizedTest(name = "bits {0}")
    @CsvSource({"1010", "1100", "0110", "1011001", "10", "0100001"})
    @DisplayName("FSK survives a noiseless channel")
    void fskRoundTrip(String pattern) {
        int[] bits = parse(pattern);

        Signal modulated = Modulator.fsk(bits, TONE_FOR_ZERO, TONE_FOR_ONE, BIT_DURATION,
                SAMPLING_RATE);

        assertArrayEquals(bits,
                Demodulator.fsk(modulated, TONE_FOR_ZERO, TONE_FOR_ONE, BIT_DURATION));
    }

    @ParameterizedTest(name = "bits {0}")
    @CsvSource({"1010", "1100", "0110", "1011001", "10", "0100001"})
    @DisplayName("PSK survives a noiseless channel")
    void pskRoundTrip(String pattern) {
        int[] bits = parse(pattern);

        Signal modulated = Modulator.psk(bits, CARRIER, BIT_DURATION, SAMPLING_RATE);

        assertArrayEquals(bits, Demodulator.psk(modulated, CARRIER, BIT_DURATION));
    }

    @Test
    @DisplayName("a whole message survives all three schemes")
    void carriesTextThroughEveryScheme() {
        int[] bits = Bits.fromText("Signal");

        Signal ask = Modulator.ask(bits, LOW_AMPLITUDE, HIGH_AMPLITUDE, CARRIER, BIT_DURATION,
                SAMPLING_RATE);
        Signal fsk = Modulator.fsk(bits, TONE_FOR_ZERO, TONE_FOR_ONE, BIT_DURATION,
                SAMPLING_RATE);
        Signal psk = Modulator.psk(bits, CARRIER, BIT_DURATION, SAMPLING_RATE);

        assertEquals("Signal", Bits.toText(Demodulator.ask(ask, CARRIER, BIT_DURATION)));
        assertEquals("Signal", Bits.toText(
                Demodulator.fsk(fsk, TONE_FOR_ZERO, TONE_FOR_ONE, BIT_DURATION)));
        assertEquals("Signal", Bits.toText(Demodulator.psk(psk, CARRIER, BIT_DURATION)));
    }

    @Test
    void modulationProducesOneBitPeriodPerBit() {
        int[] bits = {1, 0, 1};

        Signal modulated = Modulator.psk(bits, CARRIER, BIT_DURATION, SAMPLING_RATE);

        assertEquals(3 * 100, modulated.length());
        assertEquals(SAMPLING_RATE, modulated.samplingRate());
    }

    @Test
    @DisplayName("ASK really does change amplitude and nothing else")
    void askVariesAmplitudeOnly() {
        Signal modulated = Modulator.ask(new int[] {0, 1}, LOW_AMPLITUDE, HIGH_AMPLITUDE,
                CARRIER, BIT_DURATION, SAMPLING_RATE);
        double[] samples = modulated.samples();

        double firstBitPeak = peak(samples, 0, 100);
        double secondBitPeak = peak(samples, 100, 200);

        assertEquals(LOW_AMPLITUDE, firstBitPeak, 0.02);
        assertEquals(HIGH_AMPLITUDE, secondBitPeak, 0.02);
    }

    @Test
    @DisplayName("FSK really does move the carrier between two tones")
    void fskMovesTheCarrier() {
        Signal forZeros = Modulator.fsk(new int[] {0, 0, 0, 0}, TONE_FOR_ZERO, TONE_FOR_ONE,
                BIT_DURATION, SAMPLING_RATE);
        Signal forOnes = Modulator.fsk(new int[] {1, 1, 1, 1}, TONE_FOR_ZERO, TONE_FOR_ONE,
                BIT_DURATION, SAMPLING_RATE);

        assertEquals(TONE_FOR_ZERO, Spectrum.of(forZeros).peakFrequency(), 3);
        assertEquals(TONE_FOR_ONE, Spectrum.of(forOnes).peakFrequency(), 3);
    }

    @Test
    @DisplayName("PSK keeps its amplitude and inverts the waveform instead")
    void pskInvertsThePhase() {
        Signal modulated = Modulator.psk(new int[] {0, 1}, CARRIER, BIT_DURATION,
                SAMPLING_RATE);
        double[] samples = modulated.samples();

        for (int n = 0; n < 100; n++) {
            assertEquals(samples[n], -samples[n + 100], 1e-9);
        }
    }

    @Test
    @DisplayName("mild noise does not break PSK")
    void pskToleratesMildNoise() {
        int[] bits = Bits.fromText("Signal");
        Signal modulated = Modulator.psk(bits, CARRIER, BIT_DURATION, SAMPLING_RATE);

        Signal received = Channel.PERFECT.withNoise(0.4).transmit(modulated, new Random(7));

        assertArrayEquals(bits, Demodulator.psk(received, CARRIER, BIT_DURATION));
    }

    @Test
    @DisplayName("enough noise does break it — the simulator is measuring something real")
    void heavyNoiseCorruptsTheStream() {
        int[] bits = Bits.fromText("Signal");
        Signal modulated = Modulator.psk(bits, CARRIER, BIT_DURATION, SAMPLING_RATE);

        Signal received = Channel.PERFECT.withNoise(40).transmit(modulated, new Random(7));
        int[] decoded = Demodulator.psk(received, CARRIER, BIT_DURATION);

        assertTrue(Bits.hammingDistance(bits, decoded) > 0);
    }

    @Test
    void rejectsABitPeriodShorterThanOneSample() {
        assertThrows(IllegalArgumentException.class,
                () -> Modulator.samplesPerBit(0.0001, SAMPLING_RATE));
    }

    private static int[] parse(String pattern) {
        int[] bits = new int[pattern.length()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = pattern.charAt(i) - '0';
        }
        return bits;
    }

    private static double peak(double[] samples, int from, int to) {
        double peak = 0;
        for (int n = from; n < to; n++) {
            peak = Math.max(peak, Math.abs(samples[n]));
        }
        return peak;
    }
}
