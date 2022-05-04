package tests;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.blocks.ValidatorSignature;
import catecoin.blocks.ValidatorSignatureImp;
import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

public class LedgerBlockWithSimpleBlockContentListSerializationTests {

    public LedgerBlockWithSimpleBlockContentListSerializationTests() {
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
        ProtoPojo.pojoSerializers.put(PoETDRandProof.ID, PoETDRandProof.serializer);
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
    }

    @Test
    void shouldSerializeMinimalBlock() throws Exception {
        List<UUID> prevRefs = emptyList();
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(emptyList());
        serializeLedgerBlock(prevRefs, blockContent);
    }

    @Test
    void shouldSerializeBlockWithSeveralPreviousRefs() throws Exception {
        List<UUID> prevRefs = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID());
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(emptyList());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> cpy =
                serializeLedgerBlock(prevRefs, blockContent);
        assertEquals(prevRefs, cpy.getPrevRefs());
    }

    @Test
    void shouldSerializeWithSeveralTxs() throws Exception {
        List<UUID> prevRefs = emptyList();
        List<SlimTransaction> txs = genTxs();
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(txs);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> cpy =
                serializeLedgerBlock(prevRefs, blockContent);
        assertEquals(txs, cpy.getBlockContent().getContentList());
    }

    @Test
    void shouldSerializeSeveralValidators() throws Exception {
        List<UUID> prevRefs = emptyList();
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(emptyList());
        LedgerBlock<BlockContent<SlimTransaction>, SybilElectionProof> og = genLedgerBlock(prevRefs, blockContent);
        for (int i = 0; i < 10; i++)
            og.addValidatorSignature(genValidatorSignature(og.getBlockId()));
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> cpy = genBlockCopy(og);
        assertEquals(og.getBlockId(), cpy.getBlockId());
        verifyValidatorSignaturesAreCorrect(og, cpy);
    }

    @Test
    void shouldSerializeFullBlock() throws Exception {
        List<UUID> prevRefs = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID());
        List<SlimTransaction> txs = genTxs();
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(txs);
        LedgerBlock<BlockContent<SlimTransaction>, SybilElectionProof> og = genLedgerBlock(prevRefs, blockContent);
        for (int i = 0; i < 10; i++)
            og.addValidatorSignature(genValidatorSignature(og.getBlockId()));
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> cpy = genBlockCopy(og);
        assertEquals(og.getBlockId(), cpy.getBlockId());
        assertEquals(prevRefs, cpy.getPrevRefs());
        assertEquals(txs, cpy.getBlockContent().getContentList());
        verifyValidatorSignaturesAreCorrect(og, cpy);
    }

    private LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> serializeLedgerBlock(
            List<UUID> prevRefs, BlockContent<SlimTransaction> blockContent)
            throws Exception {
        LedgerBlock<BlockContent<SlimTransaction>, SybilElectionProof> og = genLedgerBlock(prevRefs, blockContent);
        LedgerBlockImp<BlockContent<SlimTransaction>, PoETDRandProof> cpy = genBlockCopy(og);
        assertEquals(og.getBlockId(), cpy.getBlockId());
        return cpy;
    }

    @NotNull
    private LedgerBlock<BlockContent<SlimTransaction>, SybilElectionProof> genLedgerBlock(
            List<UUID> prevRefs, BlockContent<SlimTransaction> blockContent)
            throws Exception {
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        SybilElectionProof proof = new PoETDRandProof(1, new byte[0], 10, 10);
        return new LedgerBlockImp<>(1,
                prevRefs, blockContent, proof, proposer);
    }

    private LedgerBlockImp<BlockContent<SlimTransaction>, PoETDRandProof> genBlockCopy(
            LedgerBlock<BlockContent<SlimTransaction>, SybilElectionProof> og)
            throws IOException {
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = LedgerBlockImp.serializer;
        serializer.serialize(og, byteBuf);
        LedgerBlockImp<BlockContent<SlimTransaction>, PoETDRandProof> cpy =
                (LedgerBlockImp<BlockContent<SlimTransaction>, PoETDRandProof>) serializer.deserialize(byteBuf);
        return cpy;
    }

    private List<SlimTransaction> genTxs() throws Exception {
        int numTxs = 10;
        List<SlimTransaction> txs = new ArrayList<>(numTxs);
        for (int i = 0; i < numTxs; i++)
            txs.add(genTx());
        return txs;
    }

    private SlimTransaction genTx() throws Exception {
        KeyPair sender = CryptographicUtils.generateECDSAKeyPair();
        PublicKey receiver = CryptographicUtils.generateECDSAKeyPair().getPublic();
        List<UUID> inputs = List.of(randomUUID(), randomUUID(), randomUUID());
        List<Integer> outputsDestination = List.of(1,2,3,4,5);
        List<Integer> outputsOrigin = List.of(6,7,8,9,10);
        return new SlimTransaction(sender.getPublic(), receiver, inputs,
                outputsDestination, outputsOrigin, sender.getPrivate());
    }

    private ValidatorSignature genValidatorSignature(UUID blockId) throws Exception {
        KeyPair validator = CryptographicUtils.generateECDSAKeyPair();
        return new ValidatorSignatureImp(validator, blockId);
    }

    private Iterator<byte[]> getSignaturesIterator(List<ValidatorSignature> validatorSignatures) {
        return validatorSignatures.stream()
                .map(ValidatorSignature::getValidatorSignature)
                .iterator();
    }

    private void verifyValidatorSignaturesAreCorrect(LedgerBlock<BlockContent<SlimTransaction>,
            SybilElectionProof> og, LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> cpy) {
        Iterator<byte[]> ogSignatures = getSignaturesIterator(og.getSignatures());
        Iterator<byte[]> cpySignatures = getSignaturesIterator(cpy.getSignatures());
        while (ogSignatures.hasNext())
            assertArrayEquals(ogSignatures.next(), cpySignatures.next());
        assertFalse(cpySignatures.hasNext());
    }

}
