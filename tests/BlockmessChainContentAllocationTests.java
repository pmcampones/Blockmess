package tests;

import catecoin.blockConstructors.ComposableContentStorageImp;
import catecoin.blockConstructors.ContentStoragePrototype;
import catecoin.blockConstructors.ContextAwareContentStorage;
import catecoin.blockConstructors.PrototypicalContentStorage;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.mempoolManager.*;
import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import catecoin.validators.TestBlockValidator;
import ledger.PrototypicalLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.ledgerManager.nodes.DebugBlockmessChain;
import ledger.ledgerManager.nodes.ParentTreeNode;
import ledger.ledgerManager.nodes.ReferenceNode;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import main.CryptographicUtils;
import main.Main;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import sybilResistantCommitteeElection.FakeSybilElectionProof;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoETProof;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static catecoin.txs.StructuredValueSlimTransactionWrapper.wrapTx;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockmessChainContentAllocationTests {

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private DebugBlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilElectionProof> Chain;

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

    public BlockmessChainContentAllocationTests() throws Exception {
        Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);
        PrototypicalLedger<BlockmessBlock<BlockContent<IndexableContent>, SybilElectionProof>> protoLedger =
                new Blockchain<>(props, new TestBlockValidator<>(), new MinimalistBootstrapModule());
        LedgerPrototype.setPrototype(protoLedger);
        MempoolManager<StructuredValue<SlimTransaction>, BlockmessGPoETProof> mempoolManager =
                new MempoolManager<>(props, new StructuredValueChunkCreator<>(new MinimalistChunkCreator<>()),
                        new MinimalistRecordModule(props), new MinimalistBootstrapModule());
        PrototypicalContentStorage<StructuredValue<SlimTransaction>> contentStorage =
                new ContextAwareContentStorage<>(props, mempoolManager);
        ContentStoragePrototype.setPrototype(contentStorage);
        this.Chain = new ReferenceNode<>(props, new UUID(0, 0), fakeParent, 0, 1, 0,
                new ComposableContentStorageImp<>());
    }

    @BeforeClass
    public static void setUpFixture() throws Exception {

    }

    @BeforeEach
    void setUp() throws PrototypeHasNotBeenDefinedException {
        this.Chain = new ReferenceNode<>(props, new UUID(0, 0), fakeParent, 0, 1, 0,
                new ComposableContentStorageImp<>());
    }

    @Test
    void shouldHaveNoInitialContent() throws PrototypeHasNotBeenDefinedException {
        this.Chain = new ReferenceNode<>(props, new UUID(0, 0), fakeParent, 0, 1, 0,
                new ComposableContentStorageImp<>());
    }

    @Test
    void shouldContainSubmittedTransaction() throws Exception {
        StructuredValue<SlimTransaction> tx = wrapTx(genRandomTx());
        Chain.submitContent(tx);
        assertTrue(Chain.getStoredContent().contains(tx));
    }

    @Test
    void shouldGoLeft() throws Exception {
        createChains(Chain);
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        SlimTransaction tx = genRandomTx();
        byte[] match = new byte[]{0};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match, match, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().isEmpty());
        assertTrue(lft.getStoredContent().contains(structuredValue));
        assertTrue(rgt.getStoredContent().isEmpty());
    }

    @Test
    void shouldGoRight() throws Exception {
        createChains(Chain);
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        SlimTransaction tx = genRandomTx();
        byte[] match = new byte[]{(byte) 0xFF};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match, match, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().isEmpty());
        assertTrue(lft.getStoredContent().isEmpty());
        assertTrue(rgt.getStoredContent().contains(structuredValue));
    }

    @Test
    void shouldGoToCenter() throws Exception {
        createChains(Chain);
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        SlimTransaction tx = genRandomTx();
        byte[] match1 = new byte[]{(byte) 0xFF};
        byte[] match2 = new byte[]{0};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match1, match2, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().contains(structuredValue));
        assertTrue(lft.getStoredContent().isEmpty());
        assertTrue(rgt.getStoredContent().isEmpty());
    }

    @Test
    void shouldGoCenterLeft() throws Exception {
        createChains(Chain);
        createChains(Chain);
        assertEquals(2, Chain.getNumChaining());
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        var centerLft = Chain.getSpawnedChains().get(2);
        var centerRgt = Chain.getSpawnedChains().get(3);
        SlimTransaction tx = genRandomTx();
        byte[] match1 = new byte[]{0};
        byte[] match2 = new byte[]{(byte) 0b10000000};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match1, match2, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().isEmpty());
        assertTrue(lft.getStoredContent().isEmpty());
        assertTrue(rgt.getStoredContent().isEmpty());
        assertTrue(centerLft.getStoredContent().contains(structuredValue));
        assertTrue(centerRgt.getStoredContent().isEmpty());
    }

    @Test
    void shouldGoCenterCenter() throws Exception {
        createChains(Chain);
        createChains(Chain);
        assertEquals(2, Chain.getNumChaining());
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        var centerLft = Chain.getSpawnedChains().get(2);
        var centerRgt = Chain.getSpawnedChains().get(3);
        SlimTransaction tx = genRandomTx();
        byte[] match1 = new byte[]{0};
        byte[] match2 = new byte[] {(byte) 0xFF};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match1, match2, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().contains(structuredValue));
        assertTrue(lft.getStoredContent().isEmpty());
        assertTrue(rgt.getStoredContent().isEmpty());
        assertTrue(centerLft.getStoredContent().isEmpty());
        assertTrue(centerRgt.getStoredContent().isEmpty());
    }

    @Test
    void shouldGoLeftRight() throws Exception {
        createChains(Chain);
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        createChains(lft);
        var lftLft = lft.getSpawnedChains().get(0);
        var lftRgt = lft.getSpawnedChains().get(1);
        SlimTransaction tx = genRandomTx();
        byte[] match = new byte[]{0b01000000};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match, match, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().isEmpty());
        assertTrue(lft.getStoredContent().isEmpty());
        assertTrue(rgt.getStoredContent().isEmpty());
        assertTrue(lftLft.getStoredContent().isEmpty());
        assertTrue(lftRgt.getStoredContent().contains(structuredValue));
    }

    @Test
    void shouldGoLeftCenter() throws Exception {
        createChains(Chain);
        var lft = Chain.getSpawnedChains().get(0);
        var rgt = Chain.getSpawnedChains().get(1);
        createChains(lft);
        var lftLft = lft.getSpawnedChains().get(0);
        var lftRgt = lft.getSpawnedChains().get(1);
        SlimTransaction tx = genRandomTx();
        byte[] match1 = new byte[]{0};
        byte[] match2 = new byte[]{(byte) 0b01000000};
        StructuredValue<SlimTransaction> structuredValue = new StructuredValue<>(match1, match2, tx);
        Chain.submitContent(structuredValue);
        assertTrue(Chain.getStoredContent().isEmpty());
        assertTrue(lft.getStoredContent().contains(structuredValue));
        assertTrue(rgt.getStoredContent().isEmpty());
        assertTrue(lftLft.getStoredContent().isEmpty());
        assertTrue(lftRgt.getStoredContent().isEmpty());
    }

    private void createChains(
            DebugBlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>,
                    SybilElectionProof> Chain) throws Exception {
        Chain.submitBlock(spawnEmptyBlock(Chain));
        Thread.sleep(200);
        Chain.spawnChildren(Chain.getBlockR().iterator().next());
        for (int i = 0; i < 5 * Chain.getFinalizedWeight(); i++) {
            Chain.submitBlock(spawnEmptyBlock(Chain));
            Thread.sleep(200);
        }
        while (Chain.hasFinalized())
            Chain.deliverChainBlock();
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>,SybilElectionProof> spawnEmptyBlock(
            DebugBlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>,
                    SybilElectionProof> destinationChain) throws Exception {
        int inherentWeight = 1;
        List<UUID> prevRefs = List.copyOf(destinationChain.getBlockR());
        BlockContent<StructuredValue<SlimTransaction>> content = new SimpleBlockContentList<>(emptyList());
        SybilElectionProof proof = new FakeSybilElectionProof();
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        long currentRank = 0;   //Ranks don't matter for these tests.
        long nextRank = 1;
        return new BlockmessBlockImp<>(inherentWeight, prevRefs, content, proof,
                proposer, destinationChain.getChainId(), currentRank, nextRank);
    }

    private SlimTransaction genRandomTx() throws Exception {
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        PublicKey destination = CryptographicUtils.generateECDSAKeyPair().getPublic();
        List<UUID> inputs = emptyList();
        List<Integer> outputsDestination = emptyList();
        List<Integer> outputsOrigin = emptyList();
        return new SlimTransaction(proposer.getPublic(), destination, inputs,
                outputsDestination, outputsOrigin, proposer.getPrivate());
    }

}
