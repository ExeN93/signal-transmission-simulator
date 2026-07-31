package pl.zwirko.signals.core;

/**
 * Conversion between text and bit streams, plus the small bit helpers the rest of the
 * simulator needs. Bits are kept as {@code int[]} of 0/1 rather than a bit set, because every
 * stage of the transmission chain indexes single bits and the arrays stay small.
 */
public final class Bits {

    /** ASCII: seven significant bits per character. */
    public static final int ASCII_BITS = 7;

    /** Latin-1 / byte-oriented text: eight bits per character. */
    public static final int BYTE_BITS = 8;

    private Bits() {
    }

    /**
     * Encodes text as a bit stream, most significant bit first, using {@link #ASCII_BITS}
     * bits per character.
     */
    public static int[] fromText(String text) {
        return fromText(text, ASCII_BITS);
    }

    /**
     * Encodes text as a bit stream, most significant bit first.
     *
     * @param bitsPerChar how many low bits of each character are transmitted (7 or 8)
     */
    public static int[] fromText(String text, int bitsPerChar) {
        requireBitsPerChar(bitsPerChar);
        int[] bits = new int[text.length() * bitsPerChar];
        for (int i = 0; i < text.length(); i++) {
            int value = text.charAt(i);
            for (int b = 0; b < bitsPerChar; b++) {
                bits[i * bitsPerChar + b] = (value >> (bitsPerChar - 1 - b)) & 1;
            }
        }
        return bits;
    }

    /**
     * Decodes a bit stream produced by {@link #fromText(String)} back into text. A trailing
     * partial character is ignored, which is what happens after block coding pads the stream.
     */
    public static String toText(int[] bits) {
        return toText(bits, ASCII_BITS);
    }

    /** Decodes a bit stream back into text using the given number of bits per character. */
    public static String toText(int[] bits, int bitsPerChar) {
        requireBitsPerChar(bitsPerChar);
        StringBuilder text = new StringBuilder(bits.length / bitsPerChar);
        for (int i = 0; i + bitsPerChar <= bits.length; i += bitsPerChar) {
            int value = 0;
            for (int b = 0; b < bitsPerChar; b++) {
                value = (value << 1) | bits[i + b];
            }
            text.append((char) value);
        }
        return text.toString();
    }

    /** Returns a copy of {@code bits} with the bit at {@code index} flipped. */
    public static int[] flip(int[] bits, int index) {
        int[] copy = bits.clone();
        copy[index] ^= 1;
        return copy;
    }

    /**
     * Returns a copy of {@code bits} with one bit flipped in every block of
     * {@code blockLength} bits, at {@code offsetInBlock}. This is how the simulator injects
     * exactly the kind of error a Hamming code is supposed to repair.
     */
    public static int[] flipOnePerBlock(int[] bits, int blockLength, int offsetInBlock) {
        if (offsetInBlock < 0 || offsetInBlock >= blockLength) {
            throw new IllegalArgumentException(
                    "offsetInBlock must be in [0, " + blockLength + "), got " + offsetInBlock);
        }
        int[] copy = bits.clone();
        for (int start = 0; start + blockLength <= copy.length; start += blockLength) {
            copy[start + offsetInBlock] ^= 1;
        }
        return copy;
    }

    /** Number of positions at which the two streams differ, compared over the shorter one. */
    public static int hammingDistance(int[] a, int[] b) {
        int length = Math.min(a.length, b.length);
        int differences = 0;
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                differences++;
            }
        }
        return differences;
    }

    /**
     * Splits a stream into fixed-size blocks, zero-padding the final block. Both Hamming
     * codecs and the modulators consume the stream this way.
     */
    public static int[] block(int[] bits, int from, int blockLength) {
        int[] block = new int[blockLength];
        for (int i = 0; i < blockLength; i++) {
            block[i] = (from + i < bits.length) ? bits[from + i] : 0;
        }
        return block;
    }

    private static void requireBitsPerChar(int bitsPerChar) {
        if (bitsPerChar != ASCII_BITS && bitsPerChar != BYTE_BITS) {
            throw new IllegalArgumentException("bitsPerChar must be 7 or 8, got " + bitsPerChar);
        }
    }
}
