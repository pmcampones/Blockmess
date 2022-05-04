package catecoin.validators;

import catecoin.posSpecific.accountManagers.AccountManager;
import main.CryptographicUtils;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sybilResistantCommitteeElection.DRandRound;
import sybilResistantCommitteeElection.DRandUtils;
import sybilResistantCommitteeElection.pos.sortition.PoSAlgorandSortitionWithDRand;
import sybilResistantCommitteeElection.pos.sortition.proofs.KeyBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.MicroBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.LargeSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Properties;

import static java.lang.Integer.parseInt;

public class SortitionProofValidator implements SybilProofValidator<SortitionProof> {

    private static final Logger logger = LogManager.getLogger(SortitionProofValidator.class);

    private static final int ADVANCE_ROUND_MULTIPLES = 10;

    private final DRandUtils dRandUtils;

    private final int expectedProposers;

    private final int advanceRoundMultiples;

    private final AccountManager accountManager;

    public SortitionProofValidator(Properties props, AccountManager accountManager) {
        this.dRandUtils = new DRandUtils(props);
        this.expectedProposers = parseInt(props.getProperty("expectedProposers",
                String.valueOf(PoSAlgorandSortitionWithDRand.EXPECTED_PROPOSERS)));
        this.advanceRoundMultiples = parseInt(props.getProperty("advanceRoundMultiples",
                String.valueOf(ADVANCE_ROUND_MULTIPLES)));
        this.accountManager = accountManager;
    }

    @Override
    public boolean isValid(SortitionProof proof, PublicKey proposer) {
        if (proof instanceof MicroBlockSortitionProof) {
            return true;
        } else if (proof instanceof KeyBlockSortitionProof) {
            return isKeyBlockValid((KeyBlockSortitionProof) proof, proposer);
        } else {
            logger.debug("Received a proof that is neither a " +
                    "microblock sortition nor a keyblock sortition");
            return false;
        }
    }

    private boolean isKeyBlockValid(KeyBlockSortitionProof proof, PublicKey proposer) {
        return proof.getRound() % advanceRoundMultiples == 0
                && isProofValid(proof, proposer);
    }

    public boolean isProofValid(KeyBlockSortitionProof proof, PublicKey proposer) {
        return isSortitionProofValid(proof, proposer) && isNextRandCorrect(proof);
    }

    public boolean isSortitionProofValid(LargeSortitionProof proof, PublicKey proposer) {
        try {
            return tryToVerifyIsSortitionProofValid(proof, proposer);
        } catch (Exception e) {
            logger.info("Unable to verify proof validity beacause: {}", e.getMessage());
        }
        return false;
    }

    private boolean tryToVerifyIsSortitionProofValid(LargeSortitionProof proof, PublicKey proposer)
            throws Exception {
        return proof.getRound() % advanceRoundMultiples == 0
                && hasCorrectNumberOfVotes(proof, proposer)
                && isSignatureValid(proof, proposer);
    }

    public boolean hasCorrectNumberOfVotes(LargeSortitionProof proof, PublicKey proposer) {
        byte[] hashProof = proof.getHashProof();
        long hashVal = computeHashVal(hashProof);
        long maxInt = (long) (Math.pow(2, Integer.BYTES * 8) - 1);
        double probability = ((double) expectedProposers) / accountManager.getCirculationCoins();
        double hashRatio = ((double) hashVal) / maxInt;
        int proposerCoins = accountManager.getProposerCoins(proposer,
                proof.getKeyBlockId());
        return inInterval(hashRatio, proof.getVotes(), probability, proposerCoins);
    }

    public static long computeHashVal(byte[] hashBytes) {
        int hashValSigned = ByteBuffer.wrap(hashBytes, 0, Integer.BYTES).getInt();
        return Integer.toUnsignedLong(hashValSigned);
    }

    public static boolean inInterval(double hashRatio, int votes, double probability, int proposerCoins) {
        BinomialDistribution b = new BinomialDistribution(proposerCoins, probability);
        double lowerBound = votes == 0 ? .0 : b.cumulativeProbability(votes - 1);
        double upperBound = lowerBound + b.probability(votes);
        return hashRatio >= lowerBound && hashRatio < upperBound;
    }

    private boolean isSignatureValid(LargeSortitionProof proof, PublicKey proposer) throws Exception {
        DRandRound proofRound = dRandUtils.getDRandRound(proof.getRound());
        byte[] randomSeed = proofRound.getRandomnessStr().getBytes();
        byte[] signature = proof.getHashProof();
        return CryptographicUtils.verifyPojoSignature(signature, randomSeed, proposer);
    }

    public boolean isNextRandCorrect(KeyBlockSortitionProof proof) {
        try {
            return tryToVerifyIsNextRandCorrect(proof);
        } catch (Exception e) {
            logger.error("Unable to verify {}'s validity because: {}",
                    KeyBlockSortitionProof.class.getSimpleName(), e.getMessage());
        }
        return false;
    }

    private boolean tryToVerifyIsNextRandCorrect(KeyBlockSortitionProof proof) throws Exception {
        byte[] nextRand = proof.getNextRoundRandomness();
        DRandRound nextRound = dRandUtils.getDRandRound(proof.getRound() + 1);
        return Arrays.equals(nextRand, nextRound.getRandomnessStr().getBytes());
    }

}
