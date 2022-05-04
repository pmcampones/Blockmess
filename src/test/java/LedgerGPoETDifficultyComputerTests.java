package test.java;

import org.junit.jupiter.api.Test;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class LedgerGPoETDifficultyComputerTests {

    @Test
    void shouldUseCorrectNumberLeadingZeros() {
        Properties props = new Properties();
        props.setProperty("expectedNumNodes", "3");
        props.setProperty("timeBetweenQueries", "5000");
        props.setProperty("expectedTimeBetweenBlocks", "60000");
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(props);
        assertEquals(6, difficultyComputer.getNumLeadingZeros());
    }

    @Test
    void shouldRejectOverlyLargeSolution() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(5);
        byte[] solution = new byte[]{(byte) 0xFF};
        assertFalse(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldAcceptSmallSolution() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(5);
        byte[] solution = new byte[]{0};
        assertTrue(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldAcceptWithJustTheRightAmountOfZeros() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(5);
        byte sol = 7;
        byte[] solution = new byte[]{sol};
        assertTrue(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldRejectWithOneZeroTooFew() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(5);
        byte sol = (byte) (1 << (Byte.SIZE - difficultyComputer.getNumLeadingZeros() + 1));
        byte[] solution = new byte[]{sol};
        assertFalse(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldAcceptWithTwoBytes() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(5);
        byte[] solution = new byte[]{0, (byte) 0xFF};
        assertTrue(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldAcceptWithTwoBytesWithDifficultSolution() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(16);
        byte[] solution = new byte[]{0,0};
        assertTrue(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldRejectShortSolution() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(16);
        byte[] solution = new byte[]{0};
        assertFalse(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldRejectWithValueOnFirstByte() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(16);
        byte[] solution = new byte[]{1, 0};
        assertFalse(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldAcceptWithValueOnThirdByte() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(16);
        byte[] solution = new byte[]{0, 0, (byte) 0xFF};
        assertTrue(difficultyComputer.hasEnoughLeadingZeros(solution));
    }

    @Test
    void shouldIdentifySolutionWithoutZeros() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(1);
        byte[] sol = new byte[]{(byte) 0xFF};
        assertEquals(0, difficultyComputer.getSolutionLeadingZeros(sol));
    }

    @Test
    void shouldIdentifySolutionWithOneLeadingZero() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(1);
        byte[] sol = new byte[]{0b01111111};
        assertEquals(1, difficultyComputer.getSolutionLeadingZeros(sol));
    }

    @Test
    void shouldIdentifySolutionWithSevenLeadingZeros() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(1);
        byte[] sol = new byte[]{1};
        assertEquals(Byte.SIZE - 1, difficultyComputer.getSolutionLeadingZeros(sol));
    }

    @Test
    void shouldIdentifySolutionWithTwoEmptyBytes() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(1);
        byte[] sol = new byte[]{0,0};
        assertEquals(sol.length * Byte.SIZE, difficultyComputer.getSolutionLeadingZeros(sol));
    }

    @Test
    void shouldIdentifySolutionWithSeveralBytesAndLastNonZero() {
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(1);
        byte[] sol = new byte[]{0,1};
        assertEquals(sol.length * Byte.SIZE - 1, difficultyComputer.getSolutionLeadingZeros(sol));
    }

}
