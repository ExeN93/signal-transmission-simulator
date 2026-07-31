package pl.zwirko.signals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import pl.zwirko.signals.charts.BerHeatmap;
import pl.zwirko.signals.charts.Charts;
import pl.zwirko.signals.coding.ErrorCorrectingCode;
import pl.zwirko.signals.coding.Hamming1511;
import pl.zwirko.signals.coding.Hamming74;
import pl.zwirko.signals.core.Bits;
import pl.zwirko.signals.core.Signal;
import pl.zwirko.signals.core.SignalGenerator;
import pl.zwirko.signals.core.Spectrum;
import pl.zwirko.signals.transmission.BerAnalyzer;
import pl.zwirko.signals.transmission.Channel;
import pl.zwirko.signals.transmission.Transmission;
import pl.zwirko.signals.transmission.Transmission.Scheme;

/**
 * Command line entry point. Each demonstration writes its figures as PNGs into an output
 * directory, so a run produces the same pictures whether or not a display is attached.
 */
public final class Main {

    private static final String PAYLOAD = "Signal";
    private static final long SEED = 20240601L;

    /**
     * Noise amplitude for the "received" figures: enough to bury the carrier visually, not
     * enough to cost the receiver a single bit. Coherent detection integrates over a whole bit
     * period, and noise that is uncorrelated with the carrier averages away over that period.
     */
    private static final double FIGURE_NOISE = 3;

    /** Noise amplitude at which the link is genuinely in trouble. */
    private static final double BREAKING_NOISE = 12;

    /** Upper end of the BER sweeps: past this the link is broken under every scheme. */
    private static final double MAX_SWEEP_NOISE = 30;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String command = (args.length > 0) ? args[0] : "all";
        Path out = Path.of((args.length > 1) ? args[1] : "out");

        switch (command) {
            case "waveforms" -> waveforms(out);
            case "spectra" -> spectra(out);
            case "transmission" -> transmission(out);
            case "ber" -> ber(out);
            case "all" -> {
                waveforms(out);
                spectra(out);
                transmission(out);
                ber(out);
            }
            default -> {
                System.err.println("""
                        usage: signal-transmission-simulator [command] [output directory]

                        commands:
                          waveforms     synthesise sine, sawtooth, triangle and square waves
                          spectra       amplitude spectra of the same waveforms
                          transmission  send a message over ASK, FSK and PSK and read it back
                          ber           sweep the channel and chart the bit error rate
                          all           all of the above (default)
                        """);
                System.exit(2);
            }
        }
        System.out.println("Figures written to " + out.toAbsolutePath());
    }

    /** Synthesises the four basic waveforms and charts them against time. */
    private static void waveforms(Path out) throws IOException {
        double frequency = 10;
        double samplingRate = 800;
        double duration = 0.4;
        int harmonics = 10;

        Map<String, Signal> waveforms = new LinkedHashMap<>();
        waveforms.put("sine", SignalGenerator.sine(1, frequency, 0, duration, samplingRate));
        waveforms.put("sawtooth",
                SignalGenerator.sawtooth(frequency, harmonics, duration, samplingRate));
        waveforms.put("triangle",
                SignalGenerator.triangle(frequency, harmonics, duration, samplingRate));
        waveforms.put("square",
                SignalGenerator.square(frequency, harmonics, duration, samplingRate));

        for (Map.Entry<String, Signal> entry : waveforms.entrySet()) {
            Charts.save(Charts.signal(entry.getValue(), entry.getKey() + " — x(t)"),
                    out.resolve(entry.getKey() + "-time.png"));
        }

        System.out.printf(
                "Spectral lines below Nyquist at f=%.0f Hz, fs=%.0f Hz: all harmonics %d, "
                        + "odd harmonics only %d%n",
                frequency, samplingRate,
                SignalGenerator.harmonicLineCount(frequency, samplingRate),
                SignalGenerator.oddHarmonicLineCount(frequency, samplingRate));
    }

    /** Charts the amplitude spectra of the same waveforms, linear and in decibels. */
    private static void spectra(Path out) throws IOException {
        double frequency = 10;
        double samplingRate = 800;
        double duration = 1;
        int harmonics = 10;

        Map<String, Signal> waveforms = new LinkedHashMap<>();
        waveforms.put("sine", SignalGenerator.sine(1, frequency, 0, duration, samplingRate));
        waveforms.put("sawtooth",
                SignalGenerator.sawtooth(frequency, harmonics, duration, samplingRate));
        waveforms.put("square",
                SignalGenerator.square(frequency, harmonics, duration, samplingRate));

        for (Map.Entry<String, Signal> entry : waveforms.entrySet()) {
            Spectrum spectrum = Spectrum.of(entry.getValue());
            Charts.save(Charts.spectrum(spectrum, entry.getKey() + " — amplitude spectrum"),
                    out.resolve(entry.getKey() + "-spectrum.png"));
            Charts.save(
                    Charts.spectrumDecibels(spectrum, entry.getKey() + " — spectrum [dB]"),
                    out.resolve(entry.getKey() + "-spectrum-db.png"));
            System.out.printf(
                    "%-9s peak %6.1f Hz   B3dB %6.1f Hz   B6dB %6.1f Hz   B10dB %6.1f Hz%n",
                    entry.getKey(), spectrum.peakFrequency(), spectrum.bandwidth(3),
                    spectrum.bandwidth(6), spectrum.bandwidth(10));
        }
    }

    /** Sends a short message over each scheme on a clean, a noisy and a hopeless channel. */
    private static void transmission(Path out) throws IOException {
        int[] payload = Bits.fromText(PAYLOAD);
        Transmission.Settings settings = Transmission.Settings.defaults();
        ErrorCorrectingCode code = new Hamming74();
        int windowBits = 6;

        System.out.printf("%-5s %-24s %-24s %s%n",
                "", "clean", "α = " + FIGURE_NOISE, "α = " + BREAKING_NOISE);
        for (Scheme scheme : Scheme.values()) {
            Transmission.Result clean = Transmission.run(
                    payload, code, scheme, settings, Channel.PERFECT, new Random(SEED));
            Transmission.Result noisy = Transmission.run(
                    payload, code, scheme, settings, Channel.PERFECT.withNoise(FIGURE_NOISE),
                    new Random(SEED));
            Transmission.Result broken = Transmission.run(
                    payload, code, scheme, settings, Channel.PERFECT.withNoise(BREAKING_NOISE),
                    new Random(SEED));

            int window = windowBits * settings.samplesPerBit(code.encode(payload).length);
            String name = scheme.name().toLowerCase(Locale.ROOT);
            Charts.save(
                    Charts.signal(clean.transmitted().window(0, window),
                            scheme + " — transmitted z(t), first " + windowBits + " bits"),
                    out.resolve(name + "-transmitted.png"));
            Charts.save(
                    Charts.signal(noisy.received().window(0, window),
                            scheme + " — received at α = " + FIGURE_NOISE + ", first "
                                    + windowBits + " bits"),
                    out.resolve(name + "-received-noisy.png"));

            System.out.printf("%-5s %-24s %-24s %s%n", scheme,
                    report(clean), report(noisy), report(broken));
        }
    }

    private static String report(Transmission.Result result) {
        return String.format("%-9s BER %5.1f%%",
                quoted(Bits.toText(result.decoded())), result.bitErrorPercent());
    }

    /** Sweeps the channel and charts how the error rate responds. */
    private static void ber(Path out) throws IOException {
        int[] payload = Bits.fromText(PAYLOAD.repeat(4));
        Transmission.Settings settings = Transmission.Settings.defaults();
        double[] noise = BerAnalyzer.linearRange(0, MAX_SWEEP_NOISE, 10);
        double[] decay = BerAnalyzer.linearRange(0, 2, 10);

        for (ErrorCorrectingCode code : new ErrorCorrectingCode[] {
                new Hamming74(), new Hamming1511()}) {
            String codeName = code.name().replaceAll("[^0-9]+", "-").replaceAll("^-|-$", "");
            System.out.printf("%n%s, BER [%%] against noise amplitude α%n", code.name());
            System.out.print("   α  ");
            for (double alpha : noise) {
                System.out.printf("%7.1f", alpha);
            }
            System.out.println();

            Map<String, double[][]> series = new LinkedHashMap<>();
            for (Scheme scheme : Scheme.values()) {
                double[] rates = BerAnalyzer.sweepNoise(
                        payload, code, scheme, settings, noise, SEED);
                series.put(scheme.name(), new double[][] {noise, percent(rates)});

                System.out.printf("%-5s ", scheme);
                for (double rate : rates) {
                    System.out.printf("%6.1f ", rate * 100);
                }
                System.out.println();

                double[][] grid = BerAnalyzer.sweep(
                        payload, code, scheme, settings, noise, decay, SEED);
                BerHeatmap.save(grid, noise, decay,
                        scheme + " over " + code.name(),
                        "noise amplitude α", "decay rate β [1/s]",
                        out.resolve("ber-" + scheme.name().toLowerCase(Locale.ROOT)
                                + "-hamming" + codeName + "-grid.png"));
            }

            Charts.save(
                    Charts.line("Bit error rate over " + code.name(),
                            "noise amplitude α", "BER [%]", series),
                    out.resolve("ber-hamming" + codeName + ".png"));
        }
    }

    private static double[] percent(double[] rates) {
        double[] scaled = new double[rates.length];
        for (int i = 0; i < rates.length; i++) {
            scaled[i] = rates[i] * 100;
        }
        return scaled;
    }

    private static String quoted(String text) {
        return "\"" + text + "\"";
    }
}
