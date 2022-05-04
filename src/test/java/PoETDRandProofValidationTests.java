package test.java;

import catecoin.txs.SlimTransaction;
import catecoin.validators.PoETProofValidator;
import ledger.blocks.BlockContent;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PoETDRandProofValidationTests {

    private final KeyPair keyPair = CryptographicUtils.generateECDSAKeyPair();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private final PoETWithDRand<BlockContent<SlimTransaction>> dwait =
            new PoETWithDRand<>(props, keyPair, null, null);

    private final PoETProofValidator validator = new PoETProofValidator(props);

    public PoETDRandProofValidationTests() throws InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, HandlerRegistrationException, InvalidParameterException, IOException {}

    @Test
    void testValidProof() throws Exception {
        PoETDRandProof proof = dwait.compileDRandProof();
        assertTrue(validator.isValid(proof, keyPair.getPublic()));
    }

    @Test
    void testAdvanceRound() throws Exception {
        PoETDRandProof og = dwait.compileDRandProof();
        PoETDRandProof forged = new PoETDRandProof(og.getRoundNumber() + 100,
                og.getRandomness(), og.getSalt(), og.getWaitTime());
        assertFalse(validator.isValid(forged, keyPair.getPublic()));
    }

    @Disabled
    @Test
    void testStaleValue() throws Exception {
        PoETDRandProof proof = dwait.compileDRandProof();
        Thread.sleep(61000); //Proofs are valid between 30 and 60 seconds.
        assertFalse(validator.isValid(proof, keyPair.getPublic()));
    }

    @Disabled
    @Test
    void testOldButStillValidValue() throws Exception {
        PoETDRandProof proof = dwait.compileDRandProof();
        Thread.sleep(31000); //Proofs are valid between 30 and 60 seconds.
        assertTrue(validator.isValid(proof, keyPair.getPublic()));
    }

    @Test
    void testForgeRandomness() throws Exception {
        PoETDRandProof og = dwait.compileDRandProof();
        PoETDRandProof forged = new PoETDRandProof(og.getRoundNumber(),
                new byte[0], og.getSalt(), og.getWaitTime());
        assertFalse(validator.isValid(forged, keyPair.getPublic()));
    }

    @Test
    void testForgeSalt() throws Exception {
        PoETDRandProof og = dwait.compileDRandProof();
        PoETDRandProof forged = new PoETDRandProof(og.getRoundNumber(),
                og.getRandomness(), og.getSalt() + 1, og.getWaitTime());
        assertFalse(validator.isValid(forged, keyPair.getPublic()));
    }

    @Test
    void testForgeProposer() throws Exception {
        PoETDRandProof og = dwait.compileDRandProof();
        PublicKey other = CryptographicUtils.generateECDSAKeyPair().getPublic();
        PoETDRandProof forged = new PoETDRandProof(og.getRoundNumber(),
                og.getRandomness(), og.getSalt(), og.getWaitTime());
        assertFalse(validator.isValid(forged, other));
    }

    @Test
    void testForgeWaitTime() throws Exception {
        PoETDRandProof og = dwait.compileDRandProof();
        PoETDRandProof forged = new PoETDRandProof(og.getRoundNumber(),
                og.getRandomness(), og.getSalt(), og.getWaitTime() - 10);
        assertFalse(validator.isValid(forged, keyPair.getPublic()));
    }

    @RepeatedTest(20)
    void testTimeDifferent() throws Exception {
        PoETDRandProof p1 = dwait.compileDRandProof();
        PoETDRandProof p2 = dwait.compileDRandProof();
        assertNotEquals(p2.getWaitTime(), p1.getWaitTime());
    }

}
