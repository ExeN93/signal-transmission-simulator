package pl.zwirko.signals.transmission;

import java.util.Random;
import pl.zwirko.signals.core.Signal;

/**
 * The transmission medium: what the waveform looks like by the time it reaches the receiver.
 *
 * <p>Three effects, each of which can be switched off independently:
 * <ul>
 *   <li><b>noise</b> — uniform random values scaled by {@code noiseAmplitude} (α) added to
 *       every sample;</li>
 *   <li><b>decay</b> — an exponential {@code e^(-βt)} envelope, the signal losing strength as
 *       it travels;</li>
 *   <li><b>suppression</b> — a linear fade to silence, complete at {@code suppressionStart},
 *       standing in for the link dropping out entirely.</li>
 * </ul>
 *
 * <p>The type is immutable and the random source is passed in per transmission, so a sweep can
 * be replayed bit for bit from a fixed seed.
 *
 * @param noiseAmplitude   α — amplitude of the additive noise, 0 for a clean channel
 * @param decayRate        β — exponential decay rate in 1/s, 0 for no decay
 * @param suppressionStart time in seconds at which the signal has faded to nothing;
 *                         {@link Double#POSITIVE_INFINITY} for no suppression
 */
public record Channel(double noiseAmplitude, double decayRate, double suppressionStart) {

    /** A channel that delivers the signal untouched — the reference every test compares to. */
    public static final Channel PERFECT =
            new Channel(0, 0, Double.POSITIVE_INFINITY);

    public Channel {
        if (noiseAmplitude < 0) {
            throw new IllegalArgumentException("noiseAmplitude must not be negative");
        }
        if (decayRate < 0) {
            throw new IllegalArgumentException("decayRate must not be negative");
        }
        if (suppressionStart <= 0) {
            throw new IllegalArgumentException("suppressionStart must be positive");
        }
    }

    /** This channel with additive noise of amplitude α. */
    public Channel withNoise(double noiseAmplitude) {
        return new Channel(noiseAmplitude, decayRate, suppressionStart);
    }

    /** This channel with an exponential decay of rate β. */
    public Channel withDecay(double decayRate) {
        return new Channel(noiseAmplitude, decayRate, suppressionStart);
    }

    /** This channel with the signal fading linearly to nothing by {@code seconds}. */
    public Channel withSuppressionAt(double seconds) {
        return new Channel(noiseAmplitude, decayRate, seconds);
    }

    /**
     * Passes {@code signal} through the channel.
     *
     * <p>Attenuation is applied before noise, which is the physical order: the medium weakens
     * what was transmitted, and the noise the receiver picks up does not shrink with it. Adding
     * noise first and attenuating afterwards would quietly improve the signal-to-noise ratio.
     */
    public Signal transmit(Signal signal, Random random) {
        double[] samples = signal.samples();
        for (int n = 0; n < samples.length; n++) {
            double t = signal.timeAt(n);
            samples[n] *= envelopeAt(t);
            if (noiseAmplitude > 0) {
                samples[n] += noiseAmplitude * (random.nextDouble() * 2 - 1);
            }
        }
        return new Signal(samples, signal.samplingRate());
    }

    /** The attenuation envelope at time {@code t}, in [0, 1]. */
    public double envelopeAt(double t) {
        double decay = (decayRate == 0) ? 1 : Math.exp(-decayRate * t);
        double fade = Double.isInfinite(suppressionStart)
                ? 1
                : Math.max(0, 1 - t / suppressionStart);
        return decay * fade;
    }

    /** True when this channel leaves the signal untouched. */
    public boolean isPerfect() {
        return noiseAmplitude == 0 && decayRate == 0 && Double.isInfinite(suppressionStart);
    }
}
