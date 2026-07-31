package pl.zwirko.signals.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpectrumTest {

    private static final double SAMPLING_RATE = 1024;

    @ParameterizedTest
    @ValueSource(doubles = {16, 64, 100, 256})
    @DisplayName("a pure tone peaks at its own frequency")
    void findsTheToneFrequency(double frequency) {
        Signal tone = SignalGenerator.sine(1, frequency, 0, 1, SAMPLING_RATE);

        Spectrum spectrum = Spectrum.of(tone);

        assertEquals(frequency, spectrum.peakFrequency(), 1.0);
    }

    @Test
    @DisplayName("a unit sine reports an amplitude of about one at its peak")
    void reportsTheToneAmplitude() {
        Signal tone = SignalGenerator.sine(1, 64, 0, 1, SAMPLING_RATE);

        double[] magnitudes = Spectrum.of(tone).magnitudes();
        double peak = 0;
        for (double magnitude : magnitudes) {
            peak = Math.max(peak, magnitude);
        }

        assertEquals(1.0, peak, 0.05);
    }

    @Test
    @DisplayName("a square wave shows odd harmonics and nothing at the even ones")
    void squareWaveHasOnlyOddHarmonics() {
        double frequency = 32;
        Signal square = SignalGenerator.square(frequency, 5, 1, SAMPLING_RATE);

        Spectrum spectrum = Spectrum.of(square);
        double[] magnitudes = spectrum.magnitudes();
        double[] frequencies = spectrum.frequencies();

        double thirdHarmonic = magnitudeAt(magnitudes, frequencies, 3 * frequency);
        double secondHarmonic = magnitudeAt(magnitudes, frequencies, 2 * frequency);

        assertTrue(thirdHarmonic > 10 * secondHarmonic,
                () -> "expected the even harmonic to be negligible, got "
                        + secondHarmonic + " against " + thirdHarmonic);
    }

    @Test
    void bandwidthGrowsAsTheThresholdRelaxes() {
        Signal square = SignalGenerator.square(32, 5, 1, SAMPLING_RATE);

        Spectrum spectrum = Spectrum.of(square);

        assertTrue(spectrum.bandwidth(3) <= spectrum.bandwidth(6));
        assertTrue(spectrum.bandwidth(6) <= spectrum.bandwidth(10));
    }

    @Test
    @DisplayName("a signal whose length is not a power of two is padded, not rejected")
    void handlesLengthsThatAreNotPowersOfTwo() {
        Signal tone = SignalGenerator.sine(1, 50, 0, 1, 1000);

        Spectrum spectrum = Spectrum.of(tone);

        assertEquals(1024 / 2, spectrum.size());
        assertEquals(50, spectrum.peakFrequency(), 1.5);
    }

    @Test
    void nextPowerOfTwoRoundsUp() {
        assertEquals(1, Spectrum.nextPowerOfTwo(1));
        assertEquals(1024, Spectrum.nextPowerOfTwo(1000));
        assertEquals(1024, Spectrum.nextPowerOfTwo(1024));
        assertEquals(2048, Spectrum.nextPowerOfTwo(1025));
    }

    private static double magnitudeAt(double[] magnitudes, double[] frequencies, double target) {
        int nearest = 0;
        for (int k = 1; k < frequencies.length; k++) {
            if (Math.abs(frequencies[k] - target) < Math.abs(frequencies[nearest] - target)) {
                nearest = k;
            }
        }
        return magnitudes[nearest];
    }
}
