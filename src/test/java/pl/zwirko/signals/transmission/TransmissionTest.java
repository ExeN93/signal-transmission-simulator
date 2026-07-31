package pl.zwirko.signals.transmission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import pl.zwirko.signals.coding.ErrorCorrectingCode;
import pl.zwirko.signals.coding.Hamming1511;
import pl.zwirko.signals.coding.Hamming74;
import pl.zwirko.signals.core.Bits;
import pl.zwirko.signals.transmission.Transmission.Scheme;

/** The whole chain, end to end. */
class TransmissionTest {

    private static final String MESSAGE = "Signal";
    private static final long SEED = 1234L;
    private static final Transmission.Settings SETTINGS = Transmission.Settings.defaults();

    static Stream<Arguments> schemesAndCodes() {
        List<Arguments> arguments = new ArrayList<>();
        for (Scheme scheme : Scheme.values()) {
            for (ErrorCorrectingCode code : List.of(new Hamming74(), new Hamming1511())) {
                arguments.add(Arguments.of(scheme, code));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} over {1}")
    @MethodSource("schemesAndCodes")
    @DisplayName("a clean channel delivers the message with no errors at all")
    void cleanChannelIsLossless(Scheme scheme, ErrorCorrectingCode code) {
        int[] payload = Bits.fromText(MESSAGE);

        Transmission.Result result = Transmission.run(
                payload, code, scheme, SETTINGS, Channel.PERFECT, new Random(SEED));

        assertEquals(0.0, result.bitErrorRate());
        assertArrayEquals(payload, result.decoded());
        assertEquals(MESSAGE, Bits.toText(result.decoded()));
    }

    @ParameterizedTest(name = "{0} over {1}")
    @MethodSource("schemesAndCodes")
    @DisplayName("the error correction repairs damage a bare link would not survive")
    void codingRepairsASingleBitPerCodeword(Scheme scheme, ErrorCorrectingCode code) {
        int[] payload = Bits.fromText(MESSAGE);
        int[] coded = code.encode(payload);

        int[] damaged = Bits.flipOnePerBlock(coded, code.codeBits(), 2);
        int[] repaired = code.decode(damaged);

        assertEquals(0.0, BerAnalyzer.bitErrorRate(payload, repaired));
        assertTrue(Bits.hammingDistance(coded, damaged) > 0, "the test damaged nothing");
    }

    @ParameterizedTest
    @EnumSource(Scheme.class)
    @DisplayName("the same seed reproduces a noisy run exactly")
    void noisyRunsAreReproducible(Scheme scheme) {
        int[] payload = Bits.fromText(MESSAGE);
        Channel channel = Channel.PERFECT.withNoise(1.5);

        Transmission.Result first = Transmission.run(
                payload, new Hamming74(), scheme, SETTINGS, channel, new Random(SEED));
        Transmission.Result second = Transmission.run(
                payload, new Hamming74(), scheme, SETTINGS, channel, new Random(SEED));

        assertArrayEquals(first.decoded(), second.decoded());
        assertEquals(first.bitErrorRate(), second.bitErrorRate());
    }

    @ParameterizedTest
    @EnumSource(Scheme.class)
    @DisplayName("enough noise does eventually break the link")
    void enoughNoiseBreaksTheLink(Scheme scheme) {
        int[] payload = Bits.fromText(MESSAGE);

        Transmission.Result result = Transmission.run(
                payload, new Hamming74(), scheme, SETTINGS,
                Channel.PERFECT.withNoise(50), new Random(SEED));

        assertTrue(result.bitErrorRate() > 0,
                scheme + " reported a clean link through overwhelming noise");
    }

    @Test
    @DisplayName("PSK holds out longer than ASK, which is the point of using it")
    void pskOutperformsAskUnderNoise() {
        int[] payload = Bits.fromText(MESSAGE.repeat(4));
        double[] noise = BerAnalyzer.linearRange(0, 3, 10);

        double[] ask = BerAnalyzer.sweepNoise(
                payload, new Hamming74(), Scheme.ASK, SETTINGS, noise, SEED);
        double[] psk = BerAnalyzer.sweepNoise(
                payload, new Hamming74(), Scheme.PSK, SETTINGS, noise, SEED);

        assertTrue(mean(psk) < mean(ask),
                () -> "PSK averaged " + mean(psk) + " against ASK's " + mean(ask));
    }

    @Test
    @DisplayName("a sweep starts clean and gets worse as the noise rises")
    void errorRateRisesWithNoise() {
        int[] payload = Bits.fromText(MESSAGE.repeat(4));
        // PSK plus Hamming(7,4) shrugs off noise of the same order as the carrier, so the
        // sweep has to reach well past α = 1 before anything shows up at all.
        double[] noise = BerAnalyzer.linearRange(0, 30, 8);

        double[] rates = BerAnalyzer.sweepNoise(
                payload, new Hamming74(), Scheme.PSK, SETTINGS, noise, SEED);

        assertEquals(0.0, rates[0], "a sweep should start on a clean channel");
        assertTrue(rates[rates.length - 1] > 0.1,
                () -> "the link should be well broken at α = 30, got "
                        + rates[rates.length - 1]);
    }

    @Test
    @DisplayName("the two-dimensional sweep fills the grid it was asked for")
    void sweepFillsTheGrid() {
        int[] payload = Bits.fromText(MESSAGE);
        double[] noise = BerAnalyzer.linearRange(0, 2, 4);
        double[] decay = BerAnalyzer.linearRange(0, 1, 3);

        double[][] grid = BerAnalyzer.sweep(
                payload, new Hamming74(), Scheme.PSK, SETTINGS, noise, decay, SEED);

        assertEquals(4, grid.length);
        for (double[] row : grid) {
            assertEquals(3, row.length);
            for (double rate : row) {
                assertTrue(rate >= 0 && rate <= 1, "error rate out of range: " + rate);
            }
        }
        assertEquals(0.0, grid[0][0], "no noise and no decay is a clean channel");
    }

    @Test
    @DisplayName("bit periods are whole numbers of samples, so the carrier phase cannot creep")
    void bitPeriodsAreWholeSamples() {
        int bitCount = 77;

        int perBit = SETTINGS.samplesPerBit(bitCount);
        double bitDuration = SETTINGS.bitDuration(bitCount);

        assertEquals(perBit / SETTINGS.samplingRate(), bitDuration);
        assertEquals(SETTINGS.carrierPeriods(),
                SETTINGS.carrierFrequency(bitCount) * bitDuration, 1e-12);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
