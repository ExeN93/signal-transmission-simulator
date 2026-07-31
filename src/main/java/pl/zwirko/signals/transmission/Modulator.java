package pl.zwirko.signals.transmission;

import pl.zwirko.signals.core.Signal;

/**
 * Digital modulators: they turn a bit stream into a carrier waveform.
 *
 * <p>All three keep the same bit duration and sampling rate, so a stream modulated one way can
 * be compared against the others sample for sample.
 */
public final class Modulator {

    private Modulator() {
    }

    /**
     * Amplitude-shift keying: the carrier frequency stays put and the amplitude switches
     * between two levels.
     *
     * @param lowAmplitude  amplitude sent for a 0 bit
     * @param highAmplitude amplitude sent for a 1 bit
     */
    public static Signal ask(int[] bits, double lowAmplitude, double highAmplitude,
                             double carrierFrequency, double bitDuration, double samplingRate) {
        return modulate(bits, bitDuration, samplingRate, (bit, t) -> {
            double amplitude = (bit == 0) ? lowAmplitude : highAmplitude;
            return amplitude * Math.sin(2 * Math.PI * carrierFrequency * t);
        });
    }

    /**
     * Frequency-shift keying: the amplitude stays put and the carrier switches between two
     * frequencies.
     */
    public static Signal fsk(int[] bits, double frequencyForZero, double frequencyForOne,
                             double bitDuration, double samplingRate) {
        return modulate(bits, bitDuration, samplingRate, (bit, t) -> {
            double frequency = (bit == 0) ? frequencyForZero : frequencyForOne;
            return Math.sin(2 * Math.PI * frequency * t);
        });
    }

    /**
     * Binary phase-shift keying: a 1 bit inverts the carrier phase. This is the scheme that
     * survives noise best of the three, because the two symbols sit as far apart as they can.
     */
    public static Signal psk(int[] bits, double carrierFrequency,
                             double bitDuration, double samplingRate) {
        return modulate(bits, bitDuration, samplingRate, (bit, t) -> {
            double phase = (bit == 0) ? 0 : Math.PI;
            return Math.sin(2 * Math.PI * carrierFrequency * t + phase);
        });
    }

    /** Samples per transmitted bit at the given bit duration and sampling rate. */
    public static int samplesPerBit(double bitDuration, double samplingRate) {
        int samples = (int) Math.round(bitDuration * samplingRate);
        if (samples < 1) {
            throw new IllegalArgumentException(
                    "bit duration " + bitDuration + " s is shorter than one sample at "
                            + samplingRate + " Hz");
        }
        return samples;
    }

    private static Signal modulate(int[] bits, double bitDuration, double samplingRate,
                                   SymbolShape shape) {
        int perBit = samplesPerBit(bitDuration, samplingRate);
        double[] samples = new double[bits.length * perBit];
        for (int n = 0; n < samples.length; n++) {
            samples[n] = shape.valueAt(bits[n / perBit], n / samplingRate);
        }
        return new Signal(samples, samplingRate);
    }

    @FunctionalInterface
    private interface SymbolShape {
        double valueAt(int bit, double t);
    }
}
