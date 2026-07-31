package pl.zwirko.signals.transmission;

import java.util.Random;
import pl.zwirko.signals.coding.ErrorCorrectingCode;
import pl.zwirko.signals.core.Bits;

/**
 * Bit error rate measurement, and the sweeps that show how it moves with channel conditions.
 */
public final class BerAnalyzer {

    private BerAnalyzer() {
    }

    /**
     * Fraction of bits that came back wrong, in [0, 1].
     *
     * <p>Compared over the length of {@code sent}: block coding pads the stream, so what comes
     * back is often longer than what went in, and counting the padding as payload would dilute
     * the result.
     */
    public static double bitErrorRate(int[] sent, int[] received) {
        if (sent.length == 0) {
            return 0;
        }
        if (received.length < sent.length) {
            throw new IllegalArgumentException(
                    "received stream is shorter than what was sent: "
                            + received.length + " < " + sent.length);
        }
        return Bits.hammingDistance(sent, received) / (double) sent.length;
    }

    /**
     * Error rate as the noise amplitude α rises, at a fixed decay rate.
     *
     * @param noiseAmplitudes the α values to try
     * @param seed            fixes the noise so the sweep can be reproduced
     * @return one error rate per α, in [0, 1]
     */
    public static double[] sweepNoise(int[] payload, ErrorCorrectingCode code,
                                      Transmission.Scheme scheme, Transmission.Settings settings,
                                      double[] noiseAmplitudes, long seed) {
        double[] rates = new double[noiseAmplitudes.length];
        for (int i = 0; i < noiseAmplitudes.length; i++) {
            Channel channel = Channel.PERFECT.withNoise(noiseAmplitudes[i]);
            rates[i] = Transmission
                    .run(payload, code, scheme, settings, channel, new Random(seed))
                    .bitErrorRate();
        }
        return rates;
    }

    /**
     * Error rate over a grid of noise amplitudes and decay rates — the surface that shows which
     * of the two effects a scheme minds more.
     *
     * @return {@code rates[i][j]} for {@code noiseAmplitudes[i]} and {@code decayRates[j]}
     */
    public static double[][] sweep(int[] payload, ErrorCorrectingCode code,
                                   Transmission.Scheme scheme, Transmission.Settings settings,
                                   double[] noiseAmplitudes, double[] decayRates, long seed) {
        double[][] rates = new double[noiseAmplitudes.length][decayRates.length];
        for (int i = 0; i < noiseAmplitudes.length; i++) {
            for (int j = 0; j < decayRates.length; j++) {
                Channel channel = Channel.PERFECT
                        .withNoise(noiseAmplitudes[i])
                        .withDecay(decayRates[j]);
                rates[i][j] = Transmission
                        .run(payload, code, scheme, settings, channel, new Random(seed))
                        .bitErrorRate();
            }
        }
        return rates;
    }

    /** {@code count} evenly spaced values from {@code from} to {@code to}, both included. */
    public static double[] linearRange(double from, double to, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("count must be at least 2, got " + count);
        }
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = from + (to - from) * i / (count - 1);
        }
        return values;
    }
}
