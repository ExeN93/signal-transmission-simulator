package pl.zwirko.signals.core;

/**
 * Generators for the periodic waveforms the simulator works with.
 *
 * <p>The sawtooth, triangle and square waves are built from their Fourier series rather than
 * from a closed form. That is deliberate: truncating the series at {@code harmonics} terms is
 * what makes the ringing show up in the spectrum, and comparing the resulting spectrum against
 * the theoretical line count is the point of the exercise.
 */
public final class SignalGenerator {

    private SignalGenerator() {
    }

    /** Sine wave of amplitude {@code amplitude} and frequency {@code frequency} Hz. */
    public static Signal sine(double amplitude, double frequency, double phase,
                              double duration, double samplingRate) {
        return Signal.of(duration, samplingRate,
                t -> amplitude * Math.sin(2 * Math.PI * frequency * t + phase));
    }

    /** Cosine wave of amplitude {@code amplitude} and frequency {@code frequency} Hz. */
    public static Signal cosine(double amplitude, double frequency, double phase,
                                double duration, double samplingRate) {
        return Signal.of(duration, samplingRate,
                t -> amplitude * Math.cos(2 * Math.PI * frequency * t + phase));
    }

    /**
     * Sawtooth wave synthesised from the first {@code harmonics} terms of its Fourier series.
     * All harmonics of the base frequency are present, so the spectrum shows a line at every
     * multiple of {@code frequency} below the Nyquist limit.
     */
    public static Signal sawtooth(double frequency, int harmonics,
                                  double duration, double samplingRate) {
        requireHarmonics(harmonics);
        return Signal.of(duration, samplingRate, t -> {
            double value = 0;
            for (int k = 1; k <= harmonics; k++) {
                value += Math.pow(-1, k + 1) * Math.sin(2 * Math.PI * k * frequency * t) / k;
            }
            return value * 2 / Math.PI;
        });
    }

    /**
     * Triangle wave synthesised from the first {@code harmonics} terms of its Fourier series.
     * Only odd harmonics contribute, and they fall off as 1/k², so the wave is noticeably
     * closer to its ideal shape than the sawtooth at the same harmonic count.
     */
    public static Signal triangle(double frequency, int harmonics,
                                  double duration, double samplingRate) {
        requireHarmonics(harmonics);
        return Signal.of(duration, samplingRate, t -> {
            double value = 0;
            for (int k = 1; k <= harmonics; k++) {
                int odd = 2 * k - 1;
                value += Math.pow(-1, k - 1) * Math.sin(2 * Math.PI * odd * frequency * t)
                        / (double) (odd * odd);
            }
            return value * 8 / (Math.PI * Math.PI);
        });
    }

    /**
     * Square wave synthesised from the first {@code harmonics} terms of its Fourier series.
     * Odd harmonics only, falling off as 1/k — this is the waveform where Gibbs overshoot at
     * the edges is easiest to see.
     */
    public static Signal square(double frequency, int harmonics,
                                double duration, double samplingRate) {
        requireHarmonics(harmonics);
        return Signal.of(duration, samplingRate, t -> {
            double value = 0;
            for (int k = 1; k <= harmonics; k++) {
                int odd = 2 * k - 1;
                value += Math.sin(2 * Math.PI * odd * frequency * t) / odd;
            }
            return value * 4 / Math.PI;
        });
    }

    /**
     * Number of spectral lines a signal containing every harmonic of {@code frequency} shows
     * below the Nyquist limit of {@code samplingRate}.
     */
    public static int harmonicLineCount(double frequency, double samplingRate) {
        int lines = 0;
        for (int k = 1; k * frequency < samplingRate / 2; k++) {
            lines++;
        }
        return lines;
    }

    /**
     * Number of spectral lines a signal containing only odd harmonics of {@code frequency}
     * shows below the Nyquist limit — the triangle and square cases.
     */
    public static int oddHarmonicLineCount(double frequency, double samplingRate) {
        int lines = 0;
        for (int k = 1; (2 * k - 1) * frequency < samplingRate / 2; k++) {
            lines++;
        }
        return lines;
    }

    private static void requireHarmonics(int harmonics) {
        if (harmonics < 1) {
            throw new IllegalArgumentException("harmonics must be at least 1, got " + harmonics);
        }
    }
}
