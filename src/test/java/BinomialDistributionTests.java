package test.java;

import catecoin.validators.SortitionProofValidator;
import org.junit.jupiter.api.RepeatedTest;
import sybilResistantCommitteeElection.pos.sortition.PoSAlgorandSortitionWithDRand;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinomialDistributionTests {

    @RepeatedTest(200)
    void testWithRandomParameters() {
        int totalCoins = 1000000;
        Random r = new Random();
        int numProposers = r.nextInt(20);
        double hashRatio = r.nextDouble(); //between 0 and 1
        int proposerCoins = r.nextInt(totalCoins);
        double probability = numProposers / ((double) totalCoins);
        int votes = PoSAlgorandSortitionWithDRand.computeNumberVotes(hashRatio, proposerCoins, probability);
        System.out.println(votes);
        assertTrue(SortitionProofValidator.inInterval(hashRatio, votes, probability, proposerCoins));
    }

    /**
     * This test helps us identify how many proposers should we expect if we want there to
     * be at least one vote in a round.
     * <p>With 6 proposers the probability of having 0 votes is very very small.</p>
     * <p>With 5 proposers sometimes there are 0 votes, however, it's rare.</p>
     */
    @RepeatedTest(500)
    void testExpectedNumberOfVotes() {
        int numberNodes = 10000;
        int totalCoins = 1000000;
        Random r = new Random();
        int numProposers = 6;//r.nextInt(10) + 1;
        double probability = numProposers / ((double) totalCoins);
        List<Double> hashRatios = IntStream.range(0, numberNodes)
                .mapToObj(i -> r.nextDouble()).collect(toList());
        List<Integer> proposerCoins = IntStream.range(0, numberNodes)
                .mapToObj(i -> totalCoins / numberNodes).collect(toList());
        List<Integer> votes = IntStream.range(0, numberNodes)
                .mapToObj(i -> PoSAlgorandSortitionWithDRand.computeNumberVotes(hashRatios.get(i),
                        proposerCoins.get(i), probability))
                .collect(toList());
        assertTrue(votes.stream().mapToInt(i -> i).sum() > 0);
    }

}
