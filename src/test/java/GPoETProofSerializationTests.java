package test.java;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.poet.gpoet.GPoETProof;

import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Random;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GPoETProofSerializationTests {

    @Test
    void shouldDeserializeTheSameAsWasSerialized() throws IOException {
        GPoETProof proofOg = new GPoETProof(new Random().nextInt());
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = proofOg.getSerializer();
        serializer.serialize(proofOg, byteBuf);
        GPoETProof proofCpy = (GPoETProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getNonce(), proofCpy.getNonce());
    }

    @Test
    void shouldDeserializeSameAsSerializedInAFullBlock() throws Exception {
        GPoETProof proof = new GPoETProof(new Random().nextInt());
        ProtoPojo.pojoSerializers.put(GPoETProof.ID, GPoETProof.serializer);
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
        BlockContent<SlimTransaction> content = new SimpleBlockContentList<>(emptyList());
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        LedgerBlock<BlockContent<SlimTransaction>, GPoETProof> blockOg =
                new LedgerBlockImp<>(1, List.of(randomUUID()), content, proof, proposer);
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = blockOg.getSerializer();
        serializer.serialize(blockOg, byteBuf);
        LedgerBlock<BlockContent<SlimTransaction>, GPoETProof> blockCpy =
                (LedgerBlock<BlockContent<SlimTransaction>, GPoETProof>) serializer.deserialize(byteBuf);
        assertEquals(proof.getNonce(), blockCpy.getSybilElectionProof().getNonce());
    }

}
