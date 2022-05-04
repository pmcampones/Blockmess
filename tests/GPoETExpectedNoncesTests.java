package tests;

import org.junit.jupiter.api.Test;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer;

import java.util.Properties;
import java.util.Random;

public class GPoETExpectedNoncesTests {

    @Test
    void shouldHaveTheExpectedNumberOfTries() {
        Properties props = new Properties();
        props.setProperty("expectedNumNodes", "1");
        props.setProperty("timeBetweenQueries", "1");
        props.setProperty("expectedTimeBetweenBlocks", "128");
        LedgerGPoETDifficultyComputer difficultyComputer = new LedgerGPoETDifficultyComputer(props);
        System.out.println("Num leading zeros required " + difficultyComputer.getNumLeadingZeros());
        float avg = 0;
        int numSamples = 10000;
        for (int i = 0; i < numSamples; i++) {
            int accum = 0;
            byte[] sol = new byte[256];
            do {
                accum++;
                new Random().nextBytes(sol);
            } while (!difficultyComputer.hasEnoughLeadingZeros(sol));
            //System.out.println(accum);
            avg += accum;
        }
        avg /= numSamples;
        System.out.println("Average tries " + avg);
    }

}
