package catecoin.validators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sybilResistantCommitteeElection.DRandRound;
import sybilResistantCommitteeElection.DRandUtils;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Properties;

public class PoETProofValidator implements SybilProofValidator<PoETDRandProof> {

    private static final Logger logger = LogManager.getLogger(PoETProofValidator.class);

    private final DRandUtils dRandUtils;

    public PoETProofValidator(Properties props) {
        this.dRandUtils = new DRandUtils(props);
    }

    /**
     * The validity of the block is dependent on timing assumptions.
     * <p>In particular, the random seed used must be at most two rounds before the latest.</p>
     * <p>The adversary is able to purposefully propose blocks such that
     * some nodes will find it valid and others won't.</p>
     * <p>The divergent state originated from this attack would turn many nodes faulty,
     * breaking the adversary model assumptions.</p>
     * <p>The timing assumptions are very lenient, such that honest blocks should all be accepted.</p>
     * <p>In real applications, this method for block validation should only be used
     * if the block's validity is based on a BFT committee in the intermediate consensus.</p>
     * <p>In such a scenario, every end user node must accept this block,
     * even if locally it does not comply with the timing assumptions.</p>
     */
    @Override
    public boolean isValid(PoETDRandProof proof, PublicKey proposer) {
        try {
            return tryToVerifyIsValid(proof, proposer);
        } catch (Exception e) {
            logger.info("Triggered exception '{}' on validation of DRandProof", e.getMessage());
        }
        return false;
    }

    private boolean tryToVerifyIsValid(PoETDRandProof proof, PublicKey proposer) throws Exception {
        DRandRound current = dRandUtils.getLatestDRandRound();
        int currentRound = current.getRound();
        int proofRound = proof.getRoundNumber();
        if (proofRound > currentRound || currentRound - proofRound > 100)   //Stale seed
            return false;
        DRandRound used = currentRound == proofRound ?
                current : dRandUtils.getDRandRound(proofRound);
        if (!Arrays.equals(proof.getRandomness(), used.getRandomnessStr().getBytes()))
            return false;
        byte[] seed = computeSeedUsed(proof, proposer);
        return PoETWithDRand.getWaitTimeFromSeed(seed) == proof.getWaitTime();
    }

    private byte[] computeSeedUsed(PoETDRandProof proof, PublicKey proposer) throws IOException {
        return PoETWithDRand.computeSeedUsed(proposer, proof.getRandomness(), proof.getSalt());
    }
}
