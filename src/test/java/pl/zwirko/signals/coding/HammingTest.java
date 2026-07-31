package pl.zwirko.signals.coding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.zwirko.signals.core.Bits;

/**
 * Both Hamming codes are held to the same contract, so the tests are written once and run
 * against each implementation.
 */
class HammingTest {

    private static final String MESSAGE = "Signal transmission";

    static Stream<ErrorCorrectingCode> codes() {
        return Stream.of(new Hamming74(), new Hamming1511());
    }

    /** Every code paired with every bit position inside one of its codewords. */
    static Stream<Arguments> codesAndErrorPositions() {
        List<Arguments> arguments = new ArrayList<>();
        codes().forEach(code -> {
            for (int position = 0; position < code.codeBits(); position++) {
                arguments.add(Arguments.of(code, position));
            }
        });
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("an undamaged codeword decodes back to the payload")
    void roundTripOverACleanChannel(ErrorCorrectingCode code) {
        int[] payload = Bits.fromText(MESSAGE);

        int[] decoded = code.decode(code.encode(payload));

        assertPayloadRecovered(payload, decoded);
    }

    @ParameterizedTest(name = "{0}: bit {1} flipped in every codeword")
    @MethodSource("codesAndErrorPositions")
    @DisplayName("a single flipped bit is corrected, wherever it lands")
    void correctsOneErrorPerCodeword(ErrorCorrectingCode code, int position) {
        int[] payload = Bits.fromText(MESSAGE);
        int[] encoded = code.encode(payload);

        int[] damaged = Bits.flipOnePerBlock(encoded, code.codeBits(), position);
        int[] decoded = code.decode(damaged);

        assertPayloadRecovered(payload, decoded);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("text survives coding, a bit error and decoding")
    void textSurvivesASingleBitErrorPerCodeword(ErrorCorrectingCode code) {
        int[] encoded = code.encode(Bits.fromText(MESSAGE));

        int[] damaged = Bits.flipOnePerBlock(encoded, code.codeBits(), 3);
        String decoded = Bits.toText(code.decode(damaged));

        assertTrue(decoded.startsWith(MESSAGE),
                () -> "expected the message back, got \"" + decoded + "\"");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("two errors in one codeword are beyond the code — as expected")
    void doesNotClaimToCorrectTwoErrors(ErrorCorrectingCode code) {
        int[] payload = Bits.fromText(MESSAGE);
        int[] encoded = code.encode(payload);

        int[] damaged = Bits.flipOnePerBlock(encoded, code.codeBits(), 0);
        damaged = Bits.flipOnePerBlock(damaged, code.codeBits(), 1);
        int[] decoded = code.decode(damaged);

        assertTrue(Bits.hammingDistance(payload, decoded) > 0,
                "a single-error-correcting code should not survive two errors per codeword");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    void encodingExpandsTheStreamByTheCodeRate(ErrorCorrectingCode code) {
        int[] payload = new int[code.dataBits() * 5];

        assertEquals(code.codeBits() * 5, code.encode(payload).length);
        assertEquals(code.dataBits() / (double) code.codeBits(), code.rate());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("a payload that does not fill the last block is zero-padded, not truncated")
    void padsTheFinalBlock(ErrorCorrectingCode code) {
        int[] payload = new int[code.dataBits() + 1];
        payload[code.dataBits()] = 1;

        int[] decoded = code.decode(code.encode(payload));

        assertEquals(2 * code.codeBits(), code.encode(payload).length);
        assertPayloadRecovered(payload, decoded);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("random payloads round-trip with one error per codeword")
    void randomPayloadsRoundTrip(ErrorCorrectingCode code) {
        Random random = new Random(4242);
        for (int trial = 0; trial < 50; trial++) {
            int[] payload = new int[code.dataBits() * 3];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = random.nextInt(2);
            }
            int[] encoded = code.encode(payload);
            int position = random.nextInt(code.codeBits());

            int[] decoded = code.decode(Bits.flipOnePerBlock(encoded, code.codeBits(), position));

            assertPayloadRecovered(payload, decoded);
        }
    }

    /**
     * Decoding returns whole blocks, so the stream can be longer than the payload that went in.
     * The comparison is over the payload; the padding is not part of the message.
     */
    private static void assertPayloadRecovered(int[] payload, int[] decoded) {
        assertTrue(decoded.length >= payload.length,
                "decoded stream is shorter than the payload");
        assertArrayEquals(payload, Arrays.copyOf(decoded, payload.length));
    }
}
