package tests;

import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.blocks.BlockContent;
import main.CryptographicUtils;
import main.Main;
import main.ProtoPojo;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.security.KeyPair;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PoETDRandProofSerializationTests {

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private final KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();

    private final PoETWithDRand<BlockContent<SlimTransaction>> poETWithDRand =
            new PoETWithDRand<>(props, proposer, null, null);

    public PoETDRandProofSerializationTests() throws Exception {}

    @Test
    void testSerializeProof() throws Exception {
        PoETDRandProof proofOg = poETWithDRand.compileDRandProof();
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = PoETDRandProof.serializer;
        serializer.serialize(proofOg, byteBuf);
        PoETDRandProof proofCpy = (PoETDRandProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getRoundNumber(), proofCpy.getRoundNumber());
        assertArrayEquals(proofOg.getRandomness(), proofCpy.getRandomness());
        assertEquals(proofOg.getSalt(), proofCpy.getSalt());
        assertEquals(proofOg.getWaitTime(), proofCpy.getWaitTime());
    }

}
