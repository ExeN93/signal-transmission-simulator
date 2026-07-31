package pl.zwirko.signals.coding;

/**
 * Hamming(15,11): eleven payload bits protected by four parity bits, correcting one flipped bit
 * per codeword.
 *
 * <p>Same idea as {@link Hamming74} one size up. Parity bits sit at the power-of-two positions
 * 1, 2, 4 and 8 (one-based), payload fills the rest, and the syndrome reads as the one-based
 * index of the corrupted bit.
 *
 * <p>The extra payload width buys a code rate of 11/15 instead of 4/7 — noticeably less
 * overhead — at the cost of protection: still only one correctable error, but now spread over a
 * longer codeword, so it holds up worse as channel noise rises.
 */
public final class Hamming1511 extends BlockCode {

    /** Zero-based positions of the payload bits inside a codeword. */
    private static final int[] DATA_POSITIONS = {2, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14};

    /** Zero-based positions of the parity bits: 1, 2, 4 and 8 one-based. */
    private static final int[] PARITY_POSITIONS = {0, 1, 3, 7};

    @Override
    public String name() {
        return "Hamming(15,11)";
    }

    @Override
    public int dataBits() {
        return 11;
    }

    @Override
    public int codeBits() {
        return 15;
    }

    @Override
    int[] encodeBlock(int[] data) {
        int[] word = new int[codeBits()];
        for (int i = 0; i < DATA_POSITIONS.length; i++) {
            word[DATA_POSITIONS[i]] = data[i];
        }
        for (int parity : PARITY_POSITIONS) {
            word[parity] = parityOver(word, parity);
        }
        return word;
    }

    @Override
    int[] decodeBlock(int[] codeword) {
        int[] word = codeword.clone();

        int errorPosition = 0;
        for (int parity : PARITY_POSITIONS) {
            if ((word[parity] ^ parityOver(word, parity)) == 1) {
                errorPosition |= parity + 1;
            }
        }
        if (errorPosition >= 1 && errorPosition <= codeBits()) {
            word[errorPosition - 1] ^= 1;
        }

        int[] data = new int[dataBits()];
        for (int i = 0; i < DATA_POSITIONS.length; i++) {
            data[i] = word[DATA_POSITIONS[i]];
        }
        return data;
    }

    /**
     * Parity over every payload position covered by the parity bit at {@code parityPosition}.
     *
     * <p>A parity bit at one-based position {@code p} (a power of two) covers exactly those
     * positions whose one-based index has the {@code p} bit set. Deriving the coverage from
     * that rule rather than hard-coding four XOR chains is what keeps the encoder and the
     * syndrome check from drifting apart.
     */
    private int parityOver(int[] word, int parityPosition) {
        int mask = parityPosition + 1;
        int parity = 0;
        for (int i = 0; i < word.length; i++) {
            int oneBased = i + 1;
            if (i != parityPosition && (oneBased & mask) != 0) {
                parity ^= word[i];
            }
        }
        return parity;
    }
}
