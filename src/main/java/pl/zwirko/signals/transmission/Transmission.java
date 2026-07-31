package pl.zwirko.signals.transmission;

import java.util.Random;
import pl.zwirko.signals.coding.ErrorCorrectingCode;
import pl.zwirko.signals.core.Bits;
import pl.zwirko.signals.core.Signal;

/**
 * The end-to-end link: payload bits in, recovered bits out.
 *
 * <pre>
 *   payload → error-correcting code → modulator → channel → demodulator → decoder → payload'
 * </pre>
 *
 * <p>Everything that varies between experiments — the code, the modulation scheme, the channel
 * — is a parameter, so a sweep over any of them is a loop rather than a copy of the pipeline.
 */
public final class Transmission {

    private Transmission() {
    }

    /** The three digital modulation schemes the simulator supports. */
    public enum Scheme {
        ASK, FSK, PSK
    }

    /**
     * Timing and amplitude settings shared by every scheme.
     *
     * <p>Bit duration is not set directly: it follows from {@code totalDuration} divided by the
     * number of bits actually on the wire, which depends on how much the error-correcting code
     * expands the payload. Carrier frequencies are then pinned to a whole number of periods per
     * bit, so a codeword always occupies the same airtime whichever code is used.
     *
     * @param totalDuration     Tc — seconds the whole transmission occupies
     * @param samplingRate      fs — sampling frequency in Hz
     * @param carrierPeriods    W — carrier periods per transmitted bit
     * @param lowAmplitude      A1 — ASK amplitude for a 0 bit
     * @param highAmplitude     A2 — ASK amplitude for a 1 bit
     */
    public record Settings(double totalDuration, double samplingRate, double carrierPeriods,
                           double lowAmplitude, double highAmplitude) {

        /** The settings used by the bundled demonstrations. */
        public static Settings defaults() {
            return new Settings(6, 2000, 2, 0.5, 1.0);
        }

        public Settings {
            if (totalDuration <= 0 || samplingRate <= 0 || carrierPeriods <= 0) {
                throw new IllegalArgumentException(
                        "totalDuration, samplingRate and carrierPeriods must be positive");
            }
            if (highAmplitude <= lowAmplitude) {
                throw new IllegalArgumentException(
                        "highAmplitude must exceed lowAmplitude, got "
                                + highAmplitude + " and " + lowAmplitude);
            }
        }

        /**
         * Samples per transmitted bit when {@code bitCount} bits share the transmission.
         *
         * <p>The bit period is snapped to a whole number of samples. Left unsnapped, the
         * nominal period {@code Tc/B} is a fraction of a sample off, the modulator lays bits
         * out on the sample grid anyway, and the carrier phase at each bit boundary creeps —
         * far enough over a long message to eat into the correlator output.
         */
        public int samplesPerBit(int bitCount) {
            int samples = (int) Math.round(totalDuration * samplingRate / bitCount);
            if (samples < 1) {
                throw new IllegalArgumentException(
                        bitCount + " bits do not fit in " + totalDuration + " s at "
                                + samplingRate + " Hz");
            }
            return samples;
        }

        /** Seconds per transmitted bit when {@code bitCount} bits share the transmission. */
        public double bitDuration(int bitCount) {
            return samplesPerBit(bitCount) / samplingRate;
        }

        /** Carrier frequency in Hz for ASK and PSK. */
        public double carrierFrequency(int bitCount) {
            return carrierPeriods / bitDuration(bitCount);
        }

        /** FSK tone for a 0 bit, in Hz. */
        public double frequencyForZero(int bitCount) {
            return (carrierPeriods + 1) / bitDuration(bitCount);
        }

        /** FSK tone for a 1 bit, in Hz. */
        public double frequencyForOne(int bitCount) {
            return (carrierPeriods + 2) / bitDuration(bitCount);
        }
    }

    /**
     * What one run of the link produced.
     *
     * @param transmitted   the modulated waveform as it left the transmitter
     * @param received      the same waveform after the channel
     * @param decoded       payload bits recovered by the receiver, truncated to the payload
     *                      length
     * @param bitErrorRate  fraction of payload bits that came back wrong, in [0, 1]
     */
    public record Result(Signal transmitted, Signal received, int[] decoded, double bitErrorRate) {

        /** The error rate as a percentage, for printing. */
        public double bitErrorPercent() {
            return bitErrorRate * 100;
        }
    }

    /**
     * Runs the whole chain once.
     *
     * @param payload the bits to send
     * @param code    the error-correcting code protecting them
     * @param scheme  how the coded bits are put on the carrier
     * @param channel what happens to the waveform on the way
     * @param random  the noise source; seed it to make a run reproducible
     */
    public static Result run(int[] payload, ErrorCorrectingCode code, Scheme scheme,
                             Settings settings, Channel channel, Random random) {
        int[] coded = code.encode(payload);
        double bitDuration = settings.bitDuration(coded.length);

        Signal transmitted = modulate(coded, scheme, settings, bitDuration);
        Signal received = channel.transmit(transmitted, random);
        int[] recovered = demodulate(received, scheme, settings, coded.length, bitDuration);

        int[] decoded = code.decode(recovered);
        int[] truncated = new int[payload.length];
        System.arraycopy(decoded, 0, truncated, 0, payload.length);

        return new Result(transmitted, received, truncated,
                BerAnalyzer.bitErrorRate(payload, truncated));
    }

    private static Signal modulate(int[] coded, Scheme scheme, Settings settings,
                                   double bitDuration) {
        int bitCount = coded.length;
        return switch (scheme) {
            case ASK -> Modulator.ask(coded, settings.lowAmplitude(), settings.highAmplitude(),
                    settings.carrierFrequency(bitCount), bitDuration, settings.samplingRate());
            case FSK -> Modulator.fsk(coded, settings.frequencyForZero(bitCount),
                    settings.frequencyForOne(bitCount), bitDuration, settings.samplingRate());
            case PSK -> Modulator.psk(coded, settings.carrierFrequency(bitCount), bitDuration,
                    settings.samplingRate());
        };
    }

    private static int[] demodulate(Signal received, Scheme scheme, Settings settings,
                                    int bitCount, double bitDuration) {
        return switch (scheme) {
            case ASK -> Demodulator.ask(received, settings.carrierFrequency(bitCount),
                    bitDuration);
            case FSK -> Demodulator.fsk(received, settings.frequencyForZero(bitCount),
                    settings.frequencyForOne(bitCount), bitDuration);
            case PSK -> Demodulator.psk(received, settings.carrierFrequency(bitCount),
                    bitDuration);
        };
    }

    /** Convenience overload taking text rather than bits. */
    public static Result run(String text, ErrorCorrectingCode code, Scheme scheme,
                             Settings settings, Channel channel, Random random) {
        return run(Bits.fromText(text), code, scheme, settings, channel, random);
    }
}
