package pl.zwirko.signals.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BitsTest {

    @ParameterizedTest
    @ValueSource(strings = {"A", "Signal", "CS2", "hello world", "~!?"})
    @DisplayName("text survives a round trip through the bit stream")
    void textRoundTrip(String text) {
        assertEquals(text, Bits.toText(Bits.fromText(text)));
    }

    @ParameterizedTest
    @ValueSource(ints = {Bits.ASCII_BITS, Bits.BYTE_BITS})
    void textRoundTripAtBothWidths(int bitsPerChar) {
        String text = "Signal";
        assertEquals(text, Bits.toText(Bits.fromText(text, bitsPerChar), bitsPerChar));
    }

    @Test
    @DisplayName("'A' is 1000001, most significant bit first")
    void encodesMostSignificantBitFirst() {
        assertArrayEquals(new int[] {1, 0, 0, 0, 0, 0, 1}, Bits.fromText("A"));
    }

    @Test
    void streamLengthIsSevenBitsPerCharacter() {
        assertEquals(7 * 6, Bits.fromText("Signal").length);
    }

    @Test
    @DisplayName("a trailing partial character is dropped rather than decoded as garbage")
    void ignoresTrailingPartialCharacter() {
        int[] bits = Bits.fromText("AB");
        int[] withPadding = new int[bits.length + 3];
        System.arraycopy(bits, 0, withPadding, 0, bits.length);

        assertEquals("AB", Bits.toText(withPadding));
    }

    @Test
    void rejectsUnsupportedCharacterWidth() {
        assertThrows(IllegalArgumentException.class, () -> Bits.fromText("A", 6));
    }

    @Test
    void flipInvertsOneBitAndLeavesTheRest() {
        int[] bits = {0, 1, 0, 1};

        assertArrayEquals(new int[] {0, 1, 1, 1}, Bits.flip(bits, 2));
        assertArrayEquals(new int[] {0, 1, 0, 1}, bits, "the input must not be modified");
    }

    @Test
    void flipOnePerBlockHitsEveryBlockOnce() {
        int[] bits = new int[12];

        int[] flipped = Bits.flipOnePerBlock(bits, 4, 1);

        assertArrayEquals(new int[] {0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0}, flipped);
    }

    @Test
    void flipOnePerBlockRejectsAnOffsetOutsideTheBlock() {
        assertThrows(IllegalArgumentException.class,
                () -> Bits.flipOnePerBlock(new int[8], 4, 4));
    }

    @Test
    void hammingDistanceCountsDifferingPositions() {
        assertEquals(0, Bits.hammingDistance(new int[] {1, 0, 1}, new int[] {1, 0, 1}));
        assertEquals(3, Bits.hammingDistance(new int[] {1, 0, 1}, new int[] {0, 1, 0}));
    }

    @Test
    void hammingDistanceComparesOverTheShorterStream() {
        assertEquals(1, Bits.hammingDistance(new int[] {1, 0}, new int[] {1, 1, 1, 1}));
    }

    @Test
    void blockZeroPadsPastTheEndOfTheStream() {
        assertArrayEquals(new int[] {1, 1, 0, 0}, Bits.block(new int[] {0, 1, 1}, 1, 4));
    }
}
