package pl.zwirko.signals.core;

/**
 * The single-sided amplitude spectrum of a {@link Signal}, computed with a radix-2 FFT.
 *
 * <p>The transform needs a power-of-two length, so signals whose sample count is not a power of
 * two are zero-padded. Padding changes the frequency resolution — the bin spacing is
 * {@code samplingRate / paddedLength}, not {@code samplingRate / sampleCount} — which is why
 * {@link #frequencies()} is derived from the padded length rather than assumed by the caller.
 */
public final class Spectrum {

    private final double[] magnitudes;
    private final double[] frequencies;

    private Spectrum(double[] magnitudes, double[] frequencies) {
        this.magnitudes = magnitudes;
        this.frequencies = frequencies;
    }

    /** Computes the amplitude spectrum of {@code signal}. */
    public static Spectrum of(Signal signal) {
        int padded = nextPowerOfTwo(signal.length());
        double[] real = new double[padded];
        double[] imaginary = new double[padded];
        System.arraycopy(signal.samples(), 0, real, 0, signal.length());

        fft(real, imaginary);

        int bins = padded / 2;
        double[] magnitudes = new double[bins];
        double[] frequencies = new double[bins];
        for (int k = 0; k < bins; k++) {
            magnitudes[k] = Math.hypot(real[k], imaginary[k]) / bins;
            frequencies[k] = k * signal.samplingRate() / padded;
        }
        return new Spectrum(magnitudes, frequencies);
    }

    /** Amplitude of each frequency bin. */
    public double[] magnitudes() {
        return magnitudes.clone();
    }

    /** Centre frequency of each bin, in Hz. */
    public double[] frequencies() {
        return frequencies.clone();
    }

    /** Number of bins in the single-sided spectrum. */
    public int size() {
        return magnitudes.length;
    }

    /**
     * Amplitudes converted to decibels. A small floor is added before the logarithm so that
     * empty bins produce a very negative number instead of negative infinity.
     */
    public double[] decibels() {
        double[] db = new double[magnitudes.length];
        for (int k = 0; k < db.length; k++) {
            db[k] = 20 * Math.log10(magnitudes[k] + 1e-12);
        }
        return db;
    }

    /** Frequency of the strongest bin, in Hz. */
    public double peakFrequency() {
        int peak = 0;
        for (int k = 1; k < magnitudes.length; k++) {
            if (magnitudes[k] > magnitudes[peak]) {
                peak = k;
            }
        }
        return frequencies[peak];
    }

    /**
     * Occupied bandwidth in Hz: the span between the lowest and highest bin still within
     * {@code dropDb} decibels of the peak.
     *
     * @param dropDb how far below the peak a bin may fall and still count, e.g. 3, 6 or 10
     */
    public double bandwidth(double dropDb) {
        double[] db = decibels();
        double peak = Double.NEGATIVE_INFINITY;
        for (double value : db) {
            peak = Math.max(peak, value);
        }
        double floor = peak - dropDb;

        int low = -1;
        int high = -1;
        for (int k = 0; k < db.length; k++) {
            if (db[k] >= floor) {
                if (low < 0) {
                    low = k;
                }
                high = k;
            }
        }
        return (low < 0) ? 0 : frequencies[high] - frequencies[low];
    }

    /**
     * In-place radix-2 decimation-in-time FFT. {@code real} and {@code imaginary} must have the
     * same power-of-two length.
     */
    static void fft(double[] real, double[] imaginary) {
        int n = real.length;
        if (n <= 1) {
            return;
        }

        double[] evenReal = new double[n / 2];
        double[] evenImaginary = new double[n / 2];
        double[] oddReal = new double[n / 2];
        double[] oddImaginary = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            evenReal[i] = real[2 * i];
            evenImaginary[i] = imaginary[2 * i];
            oddReal[i] = real[2 * i + 1];
            oddImaginary[i] = imaginary[2 * i + 1];
        }

        fft(evenReal, evenImaginary);
        fft(oddReal, oddImaginary);

        for (int k = 0; k < n / 2; k++) {
            double angle = -2 * Math.PI * k / n;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double twiddleReal = cos * oddReal[k] - sin * oddImaginary[k];
            double twiddleImaginary = sin * oddReal[k] + cos * oddImaginary[k];

            real[k] = evenReal[k] + twiddleReal;
            imaginary[k] = evenImaginary[k] + twiddleImaginary;
            real[k + n / 2] = evenReal[k] - twiddleReal;
            imaginary[k + n / 2] = evenImaginary[k] - twiddleImaginary;
        }
    }

    static int nextPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power <<= 1;
        }
        return power;
    }
}
