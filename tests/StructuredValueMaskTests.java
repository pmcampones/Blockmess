package tests;

import catecoin.blockConstructors.StructuredValueMask;
import org.junit.jupiter.api.Test;

import static catecoin.blockConstructors.StructuredValueMask.MaskResult.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StructuredValueMaskTests {

    @Test
    void shouldGoCenterDifferentBytes() {
        byte[] match1 = new byte[] {(byte) 0xFF};
        byte[] match2 = new byte[] {0};
        StructuredValueMask mask = new StructuredValueMask();
        assertEquals(CENTER, mask.matchIds(match1, match2));
    }

    @Test
    void shouldGoLeftBothZero() {
        byte[] match = new byte[]{0};
        StructuredValueMask mask = new StructuredValueMask();
        assertEquals(LEFT, mask.matchIds(match, match));
    }

    @Test
    void shouldGoRightBothOne() {
        byte[] match = new byte[]{(byte) 0xFF};
        StructuredValueMask mask = new StructuredValueMask();
        assertEquals(RIGHT, mask.matchIds(match, match));
    }

    @Test
    void shouldAdvanceAllBitsInByte() {
        byte[] match1 = new byte[]{(byte) 0b10101010};
        byte[] match2 = new byte[]{(byte) 0xF0};
        StructuredValueMask mask = new StructuredValueMask();
        assertEquals(RIGHT, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(CENTER, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(RIGHT, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(CENTER, mask.matchIds(match1, match2));
        mask.advanceMask();

        assertEquals(CENTER, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(LEFT, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(CENTER, mask.matchIds(match1, match2));
        mask.advanceMask();
        assertEquals(LEFT, mask.matchIds(match1, match2));
    }

    @Test
    void shouldStartAtMiddleOfByte() {
        byte[] match1 = new byte[]{0};
        byte[] match2 = new byte[]{(byte) 0x0F};
        StructuredValueMask mask = new StructuredValueMask(Byte.SIZE / 2);
        assertEquals(CENTER, mask.matchIds(match1, match2));
    }

    @Test
    void shouldGoToNextByte() {
        byte[] match = new byte[]{0, (byte) 0xFF};
        StructuredValueMask mask = new StructuredValueMask(Byte.SIZE - 1);
        assertEquals(LEFT, mask.matchIds(match, match));
        mask.advanceMask();
        assertEquals(RIGHT, mask.matchIds(match, match));
    }

}
