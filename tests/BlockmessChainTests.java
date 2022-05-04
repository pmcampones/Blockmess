package tests;

import catecoin.blockConstructors.ComposableContentStorageImp;
import catecoin.blockConstructors.ContentStoragePrototype;
import catecoin.blockConstructors.ContextAwareContentStorage;
import catecoin.blockConstructors.PrototypicalContentStorage;
import catecoin.mempoolManager.*;
import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import catecoin.validators.TestBlockValidator;
import io.netty.buffer.ByteBuf;
import ledger.PrototypicalLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.ledgerManager.nodes.DebugBlockmessChain;
import ledger.ledgerManager.nodes.ParentTreeNode;
import ledger.ledgerManager.nodes.ReferenceNode;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import main.CryptographicUtils;
import main.Main;
import main.ProtoPojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.FakeSybilElectionProof;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoETProof;

import java.io.IOException;
import java.security.*;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlockmessChainTests {

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private DebugBlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> og;

    private final ParentTreeNode<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>,SybilElectionProof> fakeParent = new ParentTreeNode<>() {
        @Override
        public void replaceChild(BlockmessChain newChild) {}

        @Override
        public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {}

        @Override
        public void createChains(List<BlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof>> createdChains) {
            System.out.println("Created Chains: " + createdChains.stream()
                    .map(BlockmessChain::getChainId).collect(toList()));
        }

        @Override
        public ParentTreeNode<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> getTreeRoot() {
            return this;
        }
    };

    public BlockmessChainTests() throws Exception {
        PrototypicalLedger<BlockmessBlock<BlockContent<IndexableContent>, SybilElectionProof>> protoLedger =
                new Blockchain<>(props, new TestBlockValidator<>(), new MinimalistBootstrapModule());
        LedgerPrototype.setPrototype(protoLedger);
        MempoolManager<StructuredValue<SlimTransaction>, BlockmessGPoETProof> mempoolManager =
                new MempoolManager<>(props, new StructuredValueChunkCreator<>(new MinimalistChunkCreator<>()),
                        new MinimalistRecordModule(props), new MinimalistBootstrapModule());
        PrototypicalContentStorage<StructuredValue<SlimTransaction>> contentStorage =
                new ContextAwareContentStorage<>(props, mempoolManager);
        ContentStoragePrototype.setPrototype(contentStorage);
        this.og = new ReferenceNode<>(props, new UUID(0, 0), fakeParent, 0, 1, 0,
                new ComposableContentStorageImp<>());
    }


    @BeforeEach
    void setUp() throws PrototypeHasNotBeenDefinedException {
        og = new ReferenceNode<>(props, new UUID(0, 0), fakeParent, 0, 1, 0,
                new ComposableContentStorageImp<>());
    }

    @Test
    void shouldBeLeaf() {
        assertTrue(og.isLeaf());
    }

    @Test
    void shouldBeInner() throws PrototypeHasNotBeenDefinedException {
        og.spawnChildren(og.getBlockR().iterator().next());
        assertFalse(og.isLeaf());
    }

    @Test
    void shouldGoFromLeafToInnerAndBackAgain() throws LedgerTreeNodeDoesNotExistException, PrototypeHasNotBeenDefinedException {
        og.spawnChildren(og.getBlockR().iterator().next());
        assertFalse(og.isLeaf());
        og.mergeChildren();
        assertTrue(og.isLeaf());
    }

    @Test
    void shouldNotHaveFinalized() {
        assertFalse(og.hasFinalized());
    }

    @Test
    void shouldHaveFinalizedOne() throws Exception {
        var firstBlock = og.getBlockR().iterator().next();
        submitNumBlocks(og.getFinalizedWeight());
        assertEquals(og.getFinalizedWeight() + 1, og.getNodesIds().size());
        assertEquals(1, og.getFinalizedIds().size());
        assertTrue(og.getFinalizedIds().contains(firstBlock));
    }

    private void submitNumBlocks(int numBlocks) throws Exception {
        for (int i = 0; i < numBlocks; i++) {
            BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> currBlock = genSmallBlock(i);
            og.submitBlock(currBlock);
            Thread.sleep(100);
        }
    }

    @Test
    void shouldHaveFinalizedSeveral() throws Exception {
        int several = 20;
        submitNumBlocks(og.getFinalizedWeight() + several);
        assertEquals(og.getFinalizedWeight() + several + 1, og.getNodesIds().size());
        assertEquals(several + 1, og.getFinalizedIds().size());
    }

    @Test
    void shouldNotBeUnderloadedNorOverladed() {
        assertFalse(og.isUnderloaded());
        assertFalse(og.isOverloaded());
    }

    @Test
    void shouldBeUnderloaded() throws Exception {
        assertFalse(og.isUnderloaded());
        for (int i = 0; i < og.getNumSamples() + og.getFinalizedWeight(); i++) {
            og.submitBlock(genSmallBlock(i));
            Thread.sleep(200);
        }
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertTrue(og.isUnderloaded());
    }

    @Test
    void shouldBeAlmostUnderloaded() throws Exception {
        assertFalse(og.isUnderloaded());
        int i = 0;
        for (; i < og.getFinalizedWeight() + (og.getNumSamples() / 2); i++) {
            og.submitBlock(genSmallBlock(i));
            Thread.sleep(200);
        }
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertFalse(og.isUnderloaded());
        og.submitBlock(genSmallBlock(i));
        Thread.sleep(200);
        og.deliverChainBlock();
        assertTrue(og.isUnderloaded());
    }

    @Test
    void shouldBeOverloaded() throws Exception {
        assertFalse(og.isOverloaded());
        for (int i = 0; i < og.getNumSamples() + og.getFinalizedWeight(); i++) {
            og.submitBlock(genLargeBlock(i));
            Thread.sleep(200);
        }
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertTrue(og.isOverloaded());
    }

    @Test
    void shouldBeAlmostOverloaded() throws Exception {
        assertFalse(og.isOverloaded());
        int i = 0;
        for (; i < og.getFinalizedWeight() + (og.getNumSamples() / 2); i++) {
            og.submitBlock(genLargeBlock(i));
            Thread.sleep(200);
        }
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertFalse(og.isOverloaded());
        og.submitBlock(genLargeBlock(i));
        Thread.sleep(200);
        og.deliverChainBlock();
        assertTrue(og.isOverloaded());
    }

    @Test
    void shouldHaveATemporaryChain() throws PrototypeHasNotBeenDefinedException {
        assertFalse(og.hasTemporaryChains());
        og.spawnChildren(og.getBlockR().iterator().next());
        assertTrue(og.hasTemporaryChains());
        assertEquals(1, og.getNumChaining());
    }

    @Test
    void shouldStopHavingATemporaryChain() throws Exception {
        og.spawnChildren(og.getBlockR().iterator().next());
        assertTrue(og.hasTemporaryChains());
        og.mergeChildren();
        assertFalse(og.hasTemporaryChains());
        assertEquals(0, og.getNumChaining());
    }

    @Test
    void shouldSpawnTwoChainingNodes() throws Exception {
        og.spawnChildren(og.getBlockR().iterator().next());
        og.submitBlock(genSmallBlock(1));
        og.spawnChildren(og.getBlockR().iterator().next());
        assertEquals(2, og.getNumChaining());
        assertTrue(og.hasTemporaryChains());
        og.mergeChildren();
        assertEquals(1, og.getNumChaining());
        assertTrue(og.hasTemporaryChains());
        og.mergeChildren();
        assertFalse(og.hasTemporaryChains());
        assertEquals(0, og.getNumChaining());
    }

    @Test
    void shouldTurnTemporaryChainPermanent() throws Exception {
        og.submitBlock(genSmallBlock(1));
        Thread.sleep(200);
        og.spawnChildren(og.getBlockR().iterator().next());
        submitNumBlocks(2 * og.getFinalizedWeight() + 3);
        assertTrue(og.hasFinalized());
        assertTrue(og.hasTemporaryChains());
        assertEquals(1, og.getNumChaining());
        while(og.hasFinalized())
            og.deliverChainBlock();
        assertFalse(og.hasTemporaryChains());
        assertEquals(1, og.getNumChaining());
    }

    @Test
    void shouldMergePermanentChain() throws Exception {
        shouldTurnTemporaryChainPermanent();
        og.mergeChildren();
        assertTrue(og.isLeaf());
    }

    @Test
    void shouldHaveNoConcreteChainsInTemporaryChaining() throws Exception {
        og.submitBlock(genSmallBlock(1));
        Thread.sleep(200);
        og.spawnChildren(og.getBlockR().iterator().next());
        submitNumBlocks(og.getFinalizedWeight() - 1 + 3);
        assertTrue(og.hasTemporaryChains());
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertEquals(0, og.getNumSpawnedChains());
        og.submitBlock(genSmallBlock(10));
        Thread.sleep(200);
        og.deliverChainBlock();
        assertEquals(2, og.getNumSpawnedChains());
    }

    @Test
    void shouldHaveSeveralConcreteChainsInTemporaryChaining() throws Exception {
        shouldHaveNoConcreteChainsInTemporaryChaining();
        og.submitBlock(genForkedBlock(20));
        Thread.sleep(200);
        og.submitBlock(genForkedBlock(21));
        Thread.sleep(200);
        assertEquals(6, og.getNumSpawnedChains());
    }

    @Test
    void shouldCreateConcreteChainsFromAlreadyExistantRootBlocks() throws Exception {
        og.submitBlock(genSmallBlock(1));
        Thread.sleep(200);
        UUID firstBlock = og.getBlockR().iterator().next();
        submitNumBlocks(og.getFinalizedWeight() + 3);
        og.submitBlock(genForkedBlock(20));
        Thread.sleep(200);
        og.submitBlock(genForkedBlock(21));
        Thread.sleep(200);
        og.spawnChildren(firstBlock);
        assertEquals(6, og.getNumSpawnedChains());
    }

    @Test
    void shouldDiscardChainsWhenTurningPermanent() throws Exception {
        shouldHaveSeveralConcreteChainsInTemporaryChaining();
        submitNumBlocks(og.getFinalizedWeight());
        while (og.hasFinalized())
            og.deliverChainBlock();
        assertEquals(2, og.getNumSpawnedChains());
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> genSmallBlock(int currRank)
            throws Exception {
        return genBlock(currRank, 1);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> genLargeBlock(
            int currRank) throws Exception {
        return genBlock(currRank, og.getMaxBlockSize() - 100);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> genBlock(
            int currRank, int size)
            throws Exception {
        List<UUID> prevRefs = List.of(og.getBlockR().iterator().next());
        return getBlockWithSizeAndRef(currRank, size, prevRefs);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> genForkedBlock(
            int currRank) throws Exception {
        List<UUID> prevRefs = List.of(og.getForkBlocks(1).iterator().next());
        return getBlockWithSizeAndRef(currRank, 1, prevRefs);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> getBlockWithSizeAndRef(
            int currRank, int size, List<UUID> prevRefs) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        BlockContent<StructuredValue<SlimTransaction>> content = new BlockContent<>() {
            @Override
            public boolean hasValidSemantics() {
                return true;
            }

            @Override
            public List<StructuredValue<SlimTransaction>> getContentList() {
                return Collections.emptyList();
            }

            @Override
            public byte[] getContentHash() {
                return new byte[0];
            }

            @Override
            public int getSerializedSize() {
                return size;
            }

            @Override
            public short getClassId() {
                return 0;
            }

            @Override
            public ISerializer<ProtoPojo> getSerializer() {
                return new ISerializer<>() {
                    @Override
                    public void serialize(ProtoPojo protoPojo, ByteBuf byteBuf) {
                        byte[] randomSalt = new byte[20];
                        new Random().nextBytes(randomSalt);
                        byteBuf.writeBytes(randomSalt);
                    }

                    @Override
                    public ProtoPojo deserialize(ByteBuf byteBuf) {
                        return null;
                    }
                };
            }

            @Override
            public boolean isBlocking() {
                return false;
            }

            @Override
            public UUID getBlockingID() {
                return null;
            }
        };
        SybilElectionProof proof = new FakeSybilElectionProof();
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        return new BlockmessBlockImp<>(1, prevRefs, content, proof,
                proposer, og.getChainId(), currRank, currRank +1);
    }
}
