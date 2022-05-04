package test.java;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.blocks.ValidatorSignature;
import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoETProof;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

public class BlockmessBlocksSerializationTests {

    public BlockmessBlocksSerializationTests() {
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
        ProtoPojo.pojoSerializers.put(BlockmessGPoETProof.ID, BlockmessGPoETProof.serializer);
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
    }

    @Test
    void shouldSerializeEmptyProof() throws IOException {
        BlockmessGPoETProof ogProof = new BlockmessGPoETProof(emptyList(), 10);
        BlockmessGPoETProof cpyProof = replicateProof(ogProof);
        assertEquals(ogProof.getNonce(), cpyProof.getNonce());
    }

    @Test
    void shouldSerializeFullProof() throws IOException {
        BlockmessGPoETProof ogProof = computeFullProof();
        BlockmessGPoETProof cpyProof = replicateProof(ogProof);
        assertEquals(ogProof.getNonce(), cpyProof.getNonce());
        confirmIdsEquals(ogProof, cpyProof);
        confirmByteContentEquals(ogProof, cpyProof);
    }

    @NotNull
    private BlockmessGPoETProof computeFullProof() {
        List<Pair<UUID, byte[]>> ChainSeeds = createRandomChainSeeds();
        return new BlockmessGPoETProof(ChainSeeds, 10);
    }

    private void confirmIdsEquals(BlockmessGPoETProof ogProof, BlockmessGPoETProof cpyProof) {
        List<UUID> ogIds = collectIdsFromChainSeeds(ogProof);
        List<UUID> cpyIds = collectIdsFromChainSeeds(cpyProof);
        assertEquals(ogIds, cpyIds);
    }

    private void confirmByteContentEquals(BlockmessGPoETProof ogProof, BlockmessGPoETProof cpyProof) {
        Iterator<byte[]> ogIt = collectSeedFromChainSeeds(ogProof).iterator();
        Iterator<byte[]> cpyIt = collectSeedFromChainSeeds(cpyProof).iterator();
        while(ogIt.hasNext() && cpyIt.hasNext()) {
            byte[] ogB = ogIt.next();
            byte[] cpyB = cpyIt.next();
            assertArrayEquals(ogB, cpyB);
        }
        assertFalse(ogIt.hasNext());
        assertFalse(cpyIt.hasNext());
    }

    @NotNull
    private List<byte[]> collectSeedFromChainSeeds(BlockmessGPoETProof proof) {
        return proof.getChainSeeds().stream()
                .map(Pair::getRight)
                .collect(toList());
    }

    @NotNull
    private List<UUID> collectIdsFromChainSeeds(BlockmessGPoETProof proof) {
        return proof.getChainSeeds().stream()
                .map(Pair::getLeft)
                .collect(toList());
    }

    private BlockmessGPoETProof replicateProof(BlockmessGPoETProof ogProof) throws IOException {
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = ogProof.getSerializer();
        serializer.serialize(ogProof, byteBuf);
        return (BlockmessGPoETProof) serializer.deserialize(byteBuf);
    }

    private List<Pair<UUID, byte[]>> createRandomChainSeeds() {
        List<Pair<UUID, byte[]>> ChainSeeds = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            UUID ChainId = randomUUID();
            byte[] seed = new byte[256];
            new Random().nextBytes(seed);
            ChainSeeds.add(Pair.of(ChainId, seed));
        }
        return ChainSeeds;
    }

    @Test
    void shouldSerializeEmptyBlock() throws Exception {
        BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> ogBlock =
                computeBlock(emptyList());
        BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> cpyBlock =
                replicateBlock(ogBlock);
        assertEquals(ogBlock.getBlockId(), cpyBlock.getBlockId());
        confirmSignaturesMatch(ogBlock, cpyBlock);
    }

    @Test
    void shouldSerializeFullBlock() throws Exception {
        List<SlimTransaction> txs = generateTxs();
        BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> ogBlock =
                computeBlock(txs);
        BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> cpyBlock =
                replicateBlock(ogBlock);
        assertEquals(ogBlock.getBlockId(), cpyBlock.getBlockId());
        confirmSignaturesMatch(ogBlock, cpyBlock);
    }

    @NotNull
    private BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> computeBlock(
            List<SlimTransaction> txs) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, java.security.SignatureException, java.security.InvalidKeyException, IOException {
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        BlockmessGPoETProof proof = computeFullProof();
        BlockContent<SlimTransaction> content = new SimpleBlockContentList<>(txs);
        return new BlockmessBlockImp<>(1, List.of(), content, proof,
                proposer, randomUUID(), 1, 2);
    }

    private void confirmSignaturesMatch(BlockmessBlock og, BlockmessBlock cpy) {
        Iterator<ValidatorSignature> ogSigIt = og.getSignatures().iterator();
        Iterator<ValidatorSignature> cpySigIt = cpy.getSignatures().iterator();
        while(ogSigIt.hasNext() && cpySigIt.hasNext()) {
            ValidatorSignature ogValSig = ogSigIt.next();
            ValidatorSignature cpyValSig = cpySigIt.next();
            assertEquals(ogValSig, cpyValSig);
        }
    }

    private List<SlimTransaction> generateTxs() throws Exception {
        int numTxs = 100;
        List<SlimTransaction> txs = new ArrayList<>(numTxs);
        for (int i = 0; i < numTxs; i++)
            txs.add(generateTx());
        return txs;
    }

    private SlimTransaction generateTx() throws Exception {
        KeyPair sender = CryptographicUtils.generateECDSAKeyPair();
        PublicKey destination = CryptographicUtils.generateECDSAKeyPair().getPublic();
        List<UUID> inputs = List.of(randomUUID(), randomUUID(), randomUUID());
        Random r = new Random();
        List<Integer> outDest = List.of(r.nextInt(100), r.nextInt(100), r.nextInt(100));
        List<Integer> outOg = List.of(r.nextInt(100), r.nextInt(100), r.nextInt(100));
        return new SlimTransaction(sender.getPublic(), destination, inputs, outDest, outOg, sender.getPrivate());
    }

    private BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> replicateBlock(
            BlockmessBlock<BlockContent<SlimTransaction>, BlockmessGPoETProof> ogBlock)
            throws IOException {
        ByteBuf byteBuf = Unpooled.buffer();
        ISerializer<ProtoPojo> serializer = ogBlock.getSerializer();
        serializer.serialize(ogBlock, byteBuf);
        return (BlockmessBlock) serializer.deserialize(byteBuf);
    }

}
