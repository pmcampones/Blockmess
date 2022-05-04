package tests;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import sybilResistantCommitteeElection.pos.sortition.proofs.IncompleteSortitionProof;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;

import static catecoin.validators.SortitionProofValidator.computeHashVal;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IncompleteSortitionProofComparisonTests {

    @Test
    void testSameProofDiffRounds() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof = genRandomByteArray();
        IncompleteSortitionProof proofSmall = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof);
        IncompleteSortitionProof proofBig = new IncompleteSortitionProof(round + 1, votes, keyBlockId, hashProof);
        assertTrue(proofBig.hasPriorityOver(proofSmall));
        assertFalse(proofSmall.hasPriorityOver(proofBig));
    }

    @Test
    void testSameProofDiffVotes() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof = genRandomByteArray();
        IncompleteSortitionProof proofSmall = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof);
        IncompleteSortitionProof proofBig = new IncompleteSortitionProof(round, votes + 1, keyBlockId, hashProof);
        assertTrue(proofBig.hasPriorityOver(proofSmall));
        assertFalse(proofSmall.hasPriorityOver(proofBig));
    }

    @Test
    void testRoundPriorityOverVotes() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof = genRandomByteArray();
        IncompleteSortitionProof proofSmall = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof);
        IncompleteSortitionProof proofBig = new IncompleteSortitionProof(round + 1, votes - 1, keyBlockId, hashProof);
        assertTrue(proofBig.hasPriorityOver(proofSmall));
        assertFalse(proofSmall.hasPriorityOver(proofBig));
    }

    @RepeatedTest(10)
    void testSameProofDiffHashVals() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof1 = genRandomByteArray();
        IncompleteSortitionProof proof1 = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof1);
        byte[] hashProof2 = genRandomByteArray();
        IncompleteSortitionProof proof2 = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof2);

        if (computeHashVal(hashProof1) > computeHashVal(hashProof2)) {
            assertTrue(proof1.hasPriorityOver(proof2));
            assertFalse(proof2.hasPriorityOver(proof1));
        } else {
            assertTrue(proof2.hasPriorityOver(proof1));
            assertFalse(proof1.hasPriorityOver(proof2));
        }
    }

    @RepeatedTest(10)
    void testVotesPriorityOverHashVals() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof1 = genRandomByteArray();
        IncompleteSortitionProof proofSmall = new IncompleteSortitionProof(round, votes - 1, keyBlockId, hashProof1);
        byte[] hashProof2 = genRandomByteArray();
        IncompleteSortitionProof proofBig = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof2);
        assertTrue(proofBig.hasPriorityOver(proofSmall));
        assertFalse(proofSmall.hasPriorityOver(proofBig));
    }

    @Test
    void testEqualProofs() {
        Random r = new Random();
        int round = r.nextInt(100000);
        int votes = r.nextInt(10) + 1;
        UUID keyBlockId = UUID.randomUUID();
        byte[] hashProof = genRandomByteArray();
        IncompleteSortitionProof proof = new IncompleteSortitionProof(round, votes, keyBlockId, hashProof);
        assertFalse(proof.hasPriorityOver(proof));
    }

    static byte[] genRandomByteArray() {
        long randomNum = new Random().nextLong();
        ByteBuffer byteBuf = ByteBuffer.allocate(Long.BYTES);
        byteBuf.putLong(randomNum);
        return byteBuf.array();
    }


}
