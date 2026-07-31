package pl.zwirko.signals.transmission;

import pl.zwirko.signals.core.Signal;

/**
 * Coherent demodulators. Each one multiplies the received waveform by a local copy of the
 * carrier, integrates the product over one bit period, and decides on the sign or level of the
 * result.
 *
 * <p>Integrating over a whole bit period is what buys the noise immunity: noise that is
 * uncorrelated with the carrier averages towards zero over the period, while the wanted signal
 * accumulates.
 */
public final class Demodulator {

    private Demodulator() {
    }

    /**
     * Correlates the received signal against a carrier and integrates over each bit period.
     *
     * @return one value per bit — the integral of {@code received(t) · sin(2πft + φ)}
     */
    public static double[] correlate(Signal received, double frequency, double phase,
                                     double bitDuration) {
        int perBit = Modulator.samplesPerBit(bitDuration, received.samplingRate());
        int bits = received.length() / perBit;
        double[] integrals = new double[bits];
        for (int b = 0; b < bits; b++) {
            double sum = 0;
            for (int n = b * perBit; n < (b + 1) * perBit; n++) {
                double t = received.timeAt(n);
                sum += received.sample(n) * Math.sin(2 * Math.PI * frequency * t + phase);
            }
            integrals[b] = sum;
        }
        return integrals;
    }

    /** Turns correlator output into bits: above {@code level} is a 1, at or below it a 0. */
    public static int[] decide(double[] integrals, double level) {
        int[] bits = new int[integrals.length];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = (integrals[i] > level) ? 1 : 0;
        }
        return bits;
    }

    /**
     * Demodulates ASK. Both symbols produce a positive correlation — only the magnitude differs
     * — so the decision level has to sit somewhere between the two.
     *
     * <p>The level is taken as the midpoint of the observed range rather than from the known
     * amplitudes. A real receiver does not know what was sent, and a decision level derived
     * from the transmitted bits would flatter the error rate. The cost of deciding blind is
     * that a run of identical symbols leaves no range to split, and gets read as all zeros.
     */
    public static int[] ask(Signal received, double carrierFrequency, double bitDuration) {
        double[] integrals = correlate(received, carrierFrequency, 0, bitDuration);
        return decide(integrals, midpoint(integrals));
    }

    /**
     * Demodulates BPSK. A phase-inverted symbol correlates negatively against the reference
     * carrier, so zero is the natural decision level and no amplitude estimate is needed.
     */
    public static int[] psk(Signal received, double carrierFrequency, double bitDuration) {
        double[] integrals = correlate(received, carrierFrequency, 0, bitDuration);
        // A 1 bit was sent with an inverted phase, so its correlation against the reference is
        // negative; the decision is on the negated integral.
        for (int i = 0; i < integrals.length; i++) {
            integrals[i] = -integrals[i];
        }
        return decide(integrals, 0);
    }

    /**
     * Demodulates FSK by correlating against both tones and comparing. Whichever tone
     * accumulates more energy over the bit period wins, so again no amplitude estimate is
     * needed.
     */
    public static int[] fsk(Signal received, double frequencyForZero, double frequencyForOne,
                            double bitDuration) {
        double[] forZero = correlate(received, frequencyForZero, 0, bitDuration);
        double[] forOne = correlate(received, frequencyForOne, 0, bitDuration);
        double[] difference = new double[forZero.length];
        for (int i = 0; i < difference.length; i++) {
            difference[i] = forOne[i] - forZero[i];
        }
        return decide(difference, 0);
    }

    private static double midpoint(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (values.length == 0) ? 0 : (min + max) / 2;
    }
}
