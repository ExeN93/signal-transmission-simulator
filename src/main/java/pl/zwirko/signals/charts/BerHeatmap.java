package pl.zwirko.signals.charts;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Renders a two-dimensional BER sweep as a heat map.
 *
 * <p>A sweep over noise amplitude and decay rate is a surface, and the interesting part of it is
 * where the error rate crosses from "recoverable" to "hopeless". A heat map shows that boundary
 * more directly than a 3D surface does, and it renders to a PNG with no display attached.
 */
public final class BerHeatmap {

    private static final int CELL = 34;
    private static final int MARGIN_LEFT = 76;
    private static final int MARGIN_BOTTOM = 56;
    private static final int MARGIN_TOP = 44;
    private static final int MARGIN_RIGHT = 96;
    private static final int LEGEND_WIDTH = 18;

    private BerHeatmap() {
    }

    /**
     * Draws the sweep and writes it to {@code file}.
     *
     * @param rates  {@code rates[row][column]}, each in [0, 1]
     * @param rowAxis    values along the vertical axis, one per row
     * @param columnAxis values along the horizontal axis, one per column
     */
    public static void save(double[][] rates, double[] rowAxis, double[] columnAxis,
                            String title, String rowLabel, String columnLabel, Path file)
            throws IOException {
        BufferedImage image = render(rates, rowAxis, columnAxis, title, rowLabel, columnLabel);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", file.toFile());
    }

    static BufferedImage render(double[][] rates, double[] rowAxis, double[] columnAxis,
                                String title, String rowLabel, String columnLabel) {
        int rows = rates.length;
        int columns = (rows == 0) ? 0 : rates[0].length;
        int width = MARGIN_LEFT + columns * CELL + MARGIN_RIGHT;
        int height = MARGIN_TOP + rows * CELL + MARGIN_BOTTOM;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, MARGIN_LEFT, 26);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int row = 0; row < rows; row++) {
            // Row 0 is drawn at the bottom, so the axis reads the way a plot normally does.
            int y = MARGIN_TOP + (rows - 1 - row) * CELL;
            for (int column = 0; column < columns; column++) {
                int x = MARGIN_LEFT + column * CELL;
                g.setColor(colorFor(rates[row][column]));
                g.fillRect(x, y, CELL, CELL);
                g.setColor(Color.WHITE);
                g.drawRect(x, y, CELL, CELL);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawString(String.format("%.2f", rowAxis[row]), 30, y + CELL / 2 + 4);
        }

        g.setColor(Color.DARK_GRAY);
        for (int column = 0; column < columns; column++) {
            int x = MARGIN_LEFT + column * CELL;
            g.drawString(String.format("%.2f", columnAxis[column]),
                    x + 2, MARGIN_TOP + rows * CELL + 16);
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(columnLabel, MARGIN_LEFT, height - 14);
        drawRotated(g, rowLabel, 15, MARGIN_TOP + rows * CELL / 2 + 50);

        drawLegend(g, MARGIN_LEFT + columns * CELL + 24, MARGIN_TOP, rows * CELL);

        g.dispose();
        return image;
    }

    /**
     * Maps an error rate to a colour: green where the link is clean, red where half the bits
     * are wrong. Hue alone carries the value, so the scale stays readable in greyscale print.
     */
    static Color colorFor(double rate) {
        double clamped = Math.max(0, Math.min(1, rate));
        // Above 50% wrong the link is already useless, so saturate the scale there rather than
        // spending half the colour range on differences that no longer matter.
        double scaled = Math.min(1, clamped / 0.5);
        float hue = (float) ((1 - scaled) * 0.33);
        return Color.getHSBColor(hue, 0.75f, 0.95f);
    }

    private static void drawLegend(Graphics2D g, int x, int y, int height) {
        for (int i = 0; i < height; i++) {
            double rate = 0.5 * (height - 1 - i) / (double) (height - 1);
            g.setColor(colorFor(rate));
            g.fillRect(x, y + i, LEGEND_WIDTH, 1);
        }
        g.setColor(Color.DARK_GRAY);
        g.drawRect(x, y, LEGEND_WIDTH, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString("50%", x + LEGEND_WIDTH + 4, y + 8);
        g.drawString("0%", x + LEGEND_WIDTH + 4, y + height);
        g.drawString("BER", x, y - 8);
    }

    private static void drawRotated(Graphics2D g, String text, int x, int y) {
        Graphics2D rotated = (Graphics2D) g.create();
        rotated.translate(x, y);
        rotated.rotate(-Math.PI / 2);
        rotated.drawString(text, 0, 0);
        rotated.dispose();
    }
}
