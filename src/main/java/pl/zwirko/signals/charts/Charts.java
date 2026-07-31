package pl.zwirko.signals.charts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import pl.zwirko.signals.core.Signal;
import pl.zwirko.signals.core.Spectrum;

/**
 * Chart rendering. Everything can be written straight to a PNG, so the simulator produces its
 * figures without needing a display; {@link #show} is there for stepping through a run
 * interactively.
 */
public final class Charts {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 480;

    private Charts() {
    }

    /** A signal against time. */
    public static JFreeChart signal(Signal signal, String title) {
        return line(title, "time [s]", "amplitude",
                Map.of(title, new double[][] {signal.timeAxis(), signal.samples()}));
    }

    /** A signal's amplitude spectrum. */
    public static JFreeChart spectrum(Spectrum spectrum, String title) {
        return line(title, "frequency [Hz]", "amplitude",
                Map.of(title, new double[][] {spectrum.frequencies(), spectrum.magnitudes()}));
    }

    /** A signal's amplitude spectrum on a decibel scale. */
    public static JFreeChart spectrumDecibels(Spectrum spectrum, String title) {
        return line(title, "frequency [Hz]", "amplitude [dB]",
                Map.of(title, new double[][] {spectrum.frequencies(), spectrum.decibels()}));
    }

    /**
     * One or more series sharing an x axis.
     *
     * @param series series name to {@code {x values, y values}}
     */
    public static JFreeChart line(String title, String xLabel, String yLabel,
                                  Map<String, double[][]> series) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        // Insertion order decides the legend order and the colours, so keep it stable.
        for (Map.Entry<String, double[][]> entry : new LinkedHashMap<>(series).entrySet()) {
            double[] x = entry.getValue()[0];
            double[] y = entry.getValue()[1];
            XYSeries xySeries = new XYSeries(entry.getKey());
            for (int i = 0; i < Math.min(x.length, y.length); i++) {
                xySeries.add(x[i], y[i]);
            }
            dataset.addSeries(xySeries);
        }
        return ChartFactory.createXYLineChart(title, xLabel, yLabel, dataset,
                PlotOrientation.VERTICAL, series.size() > 1, false, false);
    }

    /** Writes a chart to a PNG, creating the parent directory if needed. */
    public static void save(JFreeChart chart, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ChartUtils.saveChartAsPNG(file.toFile(), chart, WIDTH, HEIGHT);
    }

    /** Writes a chart to a PNG. */
    public static void save(JFreeChart chart, File file) throws IOException {
        save(chart, file.toPath());
    }

    /** Opens a chart in a window. Returns immediately; the window lives on the Swing thread. */
    public static void show(JFreeChart chart) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(chart.getTitle().getText());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new ChartPanel(chart));
            frame.setSize(WIDTH, HEIGHT);
            frame.setVisible(true);
        });
    }
}
