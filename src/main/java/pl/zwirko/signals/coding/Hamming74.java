package pl.zwirko.signals.coding;

/**
 * Hamming(7,4): four payload bits protected by three parity bits, correcting one flipped bit
 * per codeword.
 *
 * <p>Bits sit in the classic layout {@code [p1 p2 d1 p3 d2 d3 d4]}, i.e. the parity bits occupy
 * the power-of-two positions 1, 2 and 4 (one-based). That layout is what makes the syndrome
 * read directly as the one-based index of the corrupted bit, so correction needs no lookup
 * table.
 */
public final class Hamming74 extends BlockCode {

    @Override
    public String name() {
        return "Hamming(7,4)";
    }

    @Override
    public int dataBits() {
        return 4;
    }

    @Override
    public int codeBits() {
        return 7;
    }

    @Override
    int[] encodeBlock(int[] data) {
        int d1 = data[0];
        int d2 = data[1];
        int d3 = data[2];
        int d4 = data[3];

        int p1 = d1 ^ d2 ^ d4;
        int p2 = d1 ^ d3 ^ d4;
        int p3 = d2 ^ d3 ^ d4;

        return new int[] {p1, p2, d1, p3, d2, d3, d4};
    }

    @Override
    int[] decodeBlock(int[] codeword) {
        int[] word = codeword.clone();

        int s1 = word[0] ^ word[2] ^ word[4] ^ word[6];
        int s2 = word[1] ^ word[2] ^ word[5] ^ word[6];
        int s3 = word[3] ^ word[4] ^ word[5] ^ word[6];

        int errorPosition = (s3 << 2) | (s2 << 1) | s1;
        if (errorPosition >= 1 && errorPosition <= codeBits()) {
            word[errorPosition - 1] ^= 1;
        }

        // Read the payload out after correcting, not before — otherwise the repair is computed
        // and then thrown away.
        return new int[] {word[2], word[4], word[5], word[6]};
    }
}
