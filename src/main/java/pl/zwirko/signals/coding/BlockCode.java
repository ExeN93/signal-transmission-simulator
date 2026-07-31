package pl.zwirko.signals.coding;

import pl.zwirko.signals.core.Bits;

/**
 * Splits a bit stream into fixed-size blocks and reassembles it, so that each concrete code only
 * has to describe what happens to a single block.
 */
abstract class BlockCode implements ErrorCorrectingCode {

    /** Encodes exactly {@link #dataBits()} bits into exactly {@link #codeBits()} bits. */
    abstract int[] encodeBlock(int[] data);

    /**
     * Decodes exactly {@link #codeBits()} bits back into {@link #dataBits()} bits, repairing a
     * single flipped bit if the syndrome identifies one.
     */
    abstract int[] decodeBlock(int[] codeword);

    @Override
    public int[] encode(int[] data) {
        int blocks = ceilDiv(data.length, dataBits());
        int[] encoded = new int[blocks * codeBits()];
        for (int block = 0; block < blocks; block++) {
            int[] result = encodeBlock(Bits.block(data, block * dataBits(), dataBits()));
            System.arraycopy(result, 0, encoded, block * codeBits(), codeBits());
        }
        return encoded;
    }

    @Override
    public int[] decode(int[] codewords) {
        int blocks = ceilDiv(codewords.length, codeBits());
        int[] decoded = new int[blocks * dataBits()];
        for (int block = 0; block < blocks; block++) {
            int[] result = decodeBlock(Bits.block(codewords, block * codeBits(), codeBits()));
            System.arraycopy(result, 0, decoded, block * dataBits(), dataBits());
        }
        return decoded;
    }

    @Override
    public String toString() {
        return name();
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
