package test.java;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.pos.sortition.proofs.InElectionSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.IncompleteSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.KeyBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.MicroBlockSortitionProof;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Random;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static test.java.IncompleteSortitionProofComparisonTests.genRandomByteArray;

public class SortitionSerializationTests {

    @Test
    void testMicroblockSortitionSerialization() throws IOException {
        MicroBlockSortitionProof proofOg = new MicroBlockSortitionProof(randomUUID());
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = MicroBlockSortitionProof.serializer;
        serializer.serialize(proofOg, byteBuf);
        MicroBlockSortitionProof proofCpy = (MicroBlockSortitionProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getAssociatedKeyBlock(), proofCpy.getAssociatedKeyBlock());
    }

    @Test
    void testIncompleteSortitionProofSerialization() throws IOException {
        IncompleteSortitionProof proofOg = genIncompleteSortitionProof();
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = IncompleteSortitionProof.serializer;
        serializer.serialize(proofOg, byteBuf);
        IncompleteSortitionProof proofCpy = (IncompleteSortitionProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getRound(), proofCpy.getRound());
        assertEquals(proofOg.getVotes(), proofCpy.getVotes());
        assertEquals(proofOg.getKeyBlockId(), proofCpy.getKeyBlockId());
        assertArrayEquals(proofOg.getHashProof(), proofCpy.getHashProof());
    }

    @Test
    void testKeyBlockSortitionSerialization() throws IOException {
        IncompleteSortitionProof incomplete = genIncompleteSortitionProof();
        byte[] nextRoundRandomness = genRandomByteArray();
        KeyBlockSortitionProof proofOg = new KeyBlockSortitionProof(incomplete, nextRoundRandomness);
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = KeyBlockSortitionProof.serializer;
        serializer.serialize(proofOg, byteBuf);
        KeyBlockSortitionProof proofCpy = (KeyBlockSortitionProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getRound(), proofCpy.getRound());
        assertEquals(proofOg.getVotes(), proofCpy.getVotes());
        assertEquals(proofOg.getKeyBlockId(), proofCpy.getKeyBlockId());
        assertArrayEquals(proofOg.getHashProof(), proofCpy.getHashProof());
        assertArrayEquals(proofOg.getNextRoundRandomness(), proofCpy.getNextRoundRandomness());
    }

    @Test
    void testInElectionSortitionProofSerialization() throws Exception {
        IncompleteSortitionProof incomplete = genIncompleteSortitionProof();
        PublicKey proposer = CryptographicUtils.generateECDSAKeyPair().getPublic();
        InElectionSortitionProof proofOg = new InElectionSortitionProof(incomplete, proposer);
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = InElectionSortitionProof.serializer;
        serializer.serialize(proofOg, byteBuf);
        InElectionSortitionProof proofCpy = (InElectionSortitionProof) serializer.deserialize(byteBuf);
        assertEquals(proofOg.getRound(), proofCpy.getRound());
        assertEquals(proofOg.getVotes(), proofCpy.getVotes());
        assertEquals(proofOg.getKeyBlockId(), proofCpy.getKeyBlockId());
        assertArrayEquals(proofOg.getHashProof(), proofCpy.getHashProof());
        assertEquals(proofOg.getProposer(), proofCpy.getProposer());
    }

    private IncompleteSortitionProof genIncompleteSortitionProof() {
        int round = new Random().nextInt();
        int votes = new Random().nextInt(5) + 1;
        UUID keyBlockId = randomUUID();
        byte[] proofSignature = genRandomByteArray();
        return new IncompleteSortitionProof(round, votes,
                keyBlockId, proofSignature);
    }

}
