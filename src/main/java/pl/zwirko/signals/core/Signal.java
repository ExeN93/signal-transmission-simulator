package pl.zwirko.signals.core;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * A sampled signal: the sample values together with the sampling frequency they were taken at.
 *
 * <p>Keeping {@code fs} next to the samples is the whole point of this type. Every stage of the
 * chain needs it to turn a sample index back into a time instant, and passing it around as a
 * loose {@code double} next to a bare array is how sample rates get mismatched.
 *
 * @param samples          sample values
 * @param samplingRate     sampling frequency in Hz
 */
public record Signal(double[] samples, double samplingRate) {

    public Signal {
        if (samplingRate <= 0) {
            throw new IllegalArgumentException("samplingRate must be positive, got " + samplingRate);
        }
        samples = samples.clone();
    }

    /**
     * Samples {@code f(t)} over {@code duration} seconds at {@code samplingRate} Hz.
     *
     * @param duration     signal duration in seconds (Tc)
     * @param samplingRate sampling frequency in Hz (fs)
     * @param f            the continuous function of time being sampled
     */
    public static Signal of(double duration, double samplingRate, DoubleUnaryOperator f) {
        int count = (int) Math.round(duration * samplingRate);
        double[] samples = new double[count];
        for (int n = 0; n < count; n++) {
            samples[n] = f.applyAsDouble(n / samplingRate);
        }
        return new Signal(samples, samplingRate);
    }

    @Override
    public double[] samples() {
        return samples.clone();
    }

    /** Sample count. */
    public int length() {
        return samples.length;
    }

    /** The sample at index {@code n}. */
    public double sample(int n) {
        return samples[n];
    }

    /** Time instant of sample {@code n}, in seconds. */
    public double timeAt(int n) {
        return n / samplingRate;
    }

    /** Total duration in seconds. */
    public double duration() {
        return samples.length / samplingRate;
    }

    /** Time axis, one entry per sample — convenient when handing data to a chart. */
    public double[] timeAxis() {
        double[] time = new double[samples.length];
        for (int n = 0; n < time.length; n++) {
            time[n] = n / samplingRate;
        }
        return time;
    }

    /**
     * The samples in {@code [from, to)} as a signal in their own right.
     *
     * <p>Time restarts at zero in the window. Charts of a whole transmission are a solid block
     * of ink at any realistic sampling rate, so the figures show a window of a few bits.
     */
    public Signal window(int from, int to) {
        if (from < 0 || to > samples.length || from >= to) {
            throw new IllegalArgumentException(
                    "window [" + from + ", " + to + ") does not fit " + samples.length
                            + " samples");
        }
        return new Signal(Arrays.copyOfRange(samples, from, to), samplingRate);
    }

    /** Applies {@code op} to every sample, keeping the sampling rate. */
    public Signal map(DoubleUnaryOperator op) {
        double[] mapped = new double[samples.length];
        for (int n = 0; n < samples.length; n++) {
            mapped[n] = op.applyAsDouble(samples[n]);
        }
        return new Signal(mapped, samplingRate);
    }

    /** Sample-wise sum. Both signals must share a sampling rate and length. */
    public Signal plus(Signal other) {
        return combine(other, Double::sum);
    }

    /** Sample-wise difference. Both signals must share a sampling rate and length. */
    public Signal minus(Signal other) {
        return combine(other, (a, b) -> a - b);
    }

    /** Scales every sample by {@code factor}. */
    public Signal scaled(double factor) {
        return map(value -> value * factor);
    }

    public double min() {
        return Arrays.stream(samples).min().orElse(0);
    }

    public double max() {
        return Arrays.stream(samples).max().orElse(0);
    }

    private Signal combine(Signal other, java.util.function.DoubleBinaryOperator op) {
        requireCompatible(other);
        double[] result = new double[samples.length];
        for (int n = 0; n < samples.length; n++) {
            result[n] = op.applyAsDouble(samples[n], other.samples[n]);
        }
        return new Signal(result, samplingRate);
    }

    private void requireCompatible(Signal other) {
        if (other.samples.length != samples.length) {
            throw new IllegalArgumentException(
                    "length mismatch: " + samples.length + " vs " + other.samples.length);
        }
        if (other.samplingRate != samplingRate) {
            throw new IllegalArgumentException(
                    "sampling rate mismatch: " + samplingRate + " vs " + other.samplingRate);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Signal other
                && samplingRate == other.samplingRate
                && Arrays.equals(samples, other.samples);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(samples) + Double.hashCode(samplingRate);
    }

    @Override
    public String toString() {
        return "Signal[" + samples.length + " samples @ " + samplingRate + " Hz]";
    }
}
