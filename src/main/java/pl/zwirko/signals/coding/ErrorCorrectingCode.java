package pl.zwirko.signals.coding;

/**
 * A block error-correcting code: it turns {@link #dataBits()} bits of payload into
 * {@link #codeBits()} bits on the wire, and recovers the payload afterwards, repairing up to
 * one flipped bit per block.
 */
public interface ErrorCorrectingCode {

    /** Human-readable name, e.g. {@code "Hamming(7,4)"}. */
    String name();

    /** Payload bits per block. */
    int dataBits();

    /** Transmitted bits per block. */
    int codeBits();

    /**
     * Encodes a whole bit stream. The final block is zero-padded, so the encoded stream is
     * always a whole number of codewords.
     */
    int[] encode(int[] data);

    /**
     * Decodes a whole bit stream, correcting a single bit error in each codeword where the
     * syndrome points at one.
     *
     * <p>The returned stream contains the padding bits that {@link #encode} added, so it can be
     * longer than the original payload. Comparisons against the original are made over the
     * original length.
     */
    int[] decode(int[] codewords);

    /** Code rate: payload bits per transmitted bit. */
    default double rate() {
        return dataBits() / (double) codeBits();
    }
}
