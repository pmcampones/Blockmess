package tests;

import catecoin.blockConstructors.*;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.mempoolManager.MempoolManager;
import catecoin.mempoolManager.MempoolManagerFactory;
import catecoin.mempoolManager.MinimalistBootstrapModule;
import catecoin.txs.SlimTransaction;
import catecoin.validators.BlockValidator;
import catecoin.validators.TestBlockValidator;
import ledger.DebugLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.security.KeyPair;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link Blockchain} class.
 * In these tests its seen if the class is able to correctly add, finalize and discard blocks,
 *  even those that arrive in the wrong order.
 */
public class BlockchainTests {

    private final KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();

    private final BlockValidator<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> validator = new TestBlockValidator<>();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    //private final Blockchain<LedgerBlock<CatecoinBlockContent, PoETDRandProof>,CatecoinBlockContent, PoETDRandProof> blockchain =
    private final DebugLedger<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> blockchain =
            new Blockchain<>(props, validator, new MinimalistBootstrapModule());

    private final MempoolManager<SlimTransaction,PoETDRandProof> mempoolManager =
            MempoolManagerFactory.getMinimalistMempoolManager(props);

    private final BlockDirector<SlimTransaction, BlockContent<SlimTransaction>,LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>, PoETDRandProof> bc =
            computeBlockDirector(props, mempoolManager, proposer);

    private static BlockDirector<SlimTransaction, BlockContent<SlimTransaction>,LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>, PoETDRandProof> computeBlockDirector(Properties props, MempoolManager<SlimTransaction,PoETDRandProof> mempoolManager, KeyPair proposer){
        PrototypicalContentStorage<SlimTransaction> contentStorage = new ContextAwareContentStorage<>(props, mempoolManager);
        var contentBuilder = new SimpleBlockContentListBuilder<SlimTransaction>();
        var blockBuilder = new LedgerBlockBuilder<BlockContent<SlimTransaction>, PoETDRandProof>(new ComputeUniformWeight<>(), proposer);
        return new SimpleBlockDirector<>(contentStorage, contentBuilder, blockBuilder, proposer);
    }

    private final PoETWithDRand<BlockContent<SlimTransaction>> dwait = new PoETWithDRand<>(props, proposer, blockchain, bc);

    public BlockchainTests() throws Exception {}

    /**
     * Verifies that at the start of the {@link Blockchain}'s execution, only the genesis block is present.
     */
    @Test
    void getLastBlockIsFirst() {
        Set<UUID> last = blockchain.getBlockR();
        assertEquals(1, last.size());
    }

    /**
     * Submits several blocks to a single chain and requests the reference to the block at the end of the chain.
     * Verifies that the correct block is returned.
     */
    @Test
    void getLastBlockSeveralInSingleChain() throws Exception {
        UUID lastId = blockchain.getBlockR().iterator().next();
        for(int i = 0; i < 10; i++) {
            Set<UUID> last = blockchain.getBlockR();
            assertEquals(1, last.size());
            UUID id = last.iterator().next();
            assertEquals(lastId, id);
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = createSimpleBlock(id);
            blockchain.submitBlock(block);
            Thread.sleep(100);
            lastId = block.getBlockId();
        }
    }

    /**
     * Creates a fork in the chain and requests the last block.
     * The two resulting chains have the same length.
     * One of the two tips should be returned.
     */
    @Test
    void getLastBlockTwoMax() throws Exception {
        Set<UUID> genesisSet = blockchain.getBlockR();
        UUID genesis = genesisSet.iterator().next();
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 = createSimpleBlock(genesis);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 = createSimpleBlock(genesis);
        blockchain.submitBlock(b1);
        blockchain.submitBlock(b2);
        Thread.sleep(10);
        Set<UUID> last = blockchain.getBlockR();
        assertEquals(1, last.size());
        UUID id = last.iterator().next();
        assertTrue(id.equals(b1.getBlockId())
                || id.equals(b2.getBlockId()));
    }

    /**
     * Creates a fork in the chain and requests the last block reference twice.
     * Blocks are appended to the two forks such that on each request for the last block a different chain should be queried.
     **/
    @RepeatedTest(10)
    void getLastBlockOneBiggerFork() throws Exception {
        Set<UUID> genesisSet = blockchain.getBlockR();
        UUID genesis = genesisSet.iterator().next();
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> lastB1 = createSimpleBlock(genesis);
        blockchain.submitBlock(lastB1);
        lastB1 = createSimpleBlock(lastB1.getBlockId());
        blockchain.submitBlock(lastB1);
        System.out.println(lastB1.getBlockId());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> lastB2 = createSimpleBlock(genesis);
        blockchain.submitBlock(lastB2);
        System.out.println(lastB2.getBlockId());
        Thread.sleep(10);
        Set<UUID> last1 = blockchain.getBlockR();
        assertEquals(1, last1.size());
        UUID l1 = last1.iterator().next();
        assertEquals(l1, lastB1.getBlockId());

        lastB2 = createSimpleBlock(lastB2.getBlockId());
        blockchain.submitBlock(lastB2);
        lastB2 = createSimpleBlock(lastB2.getBlockId());
        blockchain.submitBlock(lastB2);
        Thread.sleep(10);
        Set<UUID> last2 = blockchain.getBlockR();
        assertEquals(1, last2.size());
        UUID l2 = last2.iterator().next();
        assertEquals(lastB2.getBlockId(), l2);
    }

    /**
     * Issues several blocks in a single chain following the logic the IntermediateConsensus follows when assigning a previous to a new block.
     * Verifies that all blocks are contained in the {@link Blockchain}.
     */
    @Test
    void getAllBlocksSingleChain() throws Exception {
        Set<UUID> blocks = new HashSet<>(11);
        blocks.add(blockchain.getBlockR().iterator().next());
        for (int i = 1; i < 10; i++) {
            Thread.sleep(100);
            Set<UUID> last = blockchain.getBlockR();
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                    createSimpleBlock(last.iterator().next());
            blockchain.submitBlock(block);
            blocks.add(block.getBlockId());
        }
        Thread.sleep(100);
        assertEquals(blocks, blockchain.getNodesIds());
    }

    /**
     * Creates a fork in the chain and issues new block to both forks.
     * This is done alternately so that blocks are not discarded.
     * Upon the conclusion of the block submission its verified that all blocks are contained in the {@link Blockchain}.
     */
    @Test
    void getAllBlocksWithForks() throws Exception {
        Set<UUID> blocks = new HashSet<>(11);
        blocks.add(blockchain.getBlockR().iterator().next());
        for (int i = 1; i < 11; i += 2) {
            Set<UUID> last = blockchain.getBlockR();
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 =
                    createSimpleBlock(last.iterator().next());
            blockchain.submitBlock(b1);
            blocks.add(b1.getBlockId());
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 =
                    createSimpleBlock(last.iterator().next());
            blockchain.submitBlock(b2);
            blocks.add(b2.getBlockId());
            Thread.sleep(10);
        }
        assertEquals(blocks, blockchain.getNodesIds());
    }

    /**
     * Creates a fork in the chain and expands a single chain,
     *  such that the single block in one of the chains is discarded.
     * Verifies that the block expected to be discarded is not contained in the {@link Blockchain}
     */
    @Test
    void discardedForkStart() throws Exception {
        Set<UUID> blocks = new HashSet<>();
        blocks.add(blockchain.getBlockR().iterator().next());
        Set<UUID> genesisSet = blockchain.getBlockR();
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> discarded =
                createSimpleBlock(genesisSet.iterator().next());
        blockchain.submitBlock(discarded);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 =
                createSimpleBlock(genesisSet.iterator().next());
        blockchain.submitBlock(b1);
        blocks.add(b1.getBlockId());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 =
                createSimpleBlock(b1.getBlockId());
        blockchain.submitBlock(b2);
        blocks.add(b2.getBlockId());
        for (int i = 3; i < 11; i++) {
            Thread.sleep(10);
            Set<UUID> last = blockchain.getBlockR();
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> nextBlock =
                    createSimpleBlock(last.iterator().next());
            blockchain.submitBlock(nextBlock);
            blocks.add(nextBlock.getBlockId());
        }
        Thread.sleep(10);
        assertEquals(blocks, blockchain.getNodesIds());
    }

    @Test
    void getFinalizedBlock() throws Exception {
        UUID genesis = blockchain.getBlockR().iterator().next();
        final LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> firstBlock = createSimpleBlock(genesis);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> currBlock = firstBlock;
        for (int i = 0; i < blockchain.getFinalizedWeight() + 1; i++) {
            blockchain.submitBlock(currBlock);
            Thread.sleep(100);
            currBlock = createSimpleBlock(currBlock.getBlockId());
        }
        assertEquals(2, blockchain.getFinalizedIds().size());
        assertTrue(blockchain.getFinalizedIds().contains(firstBlock.getBlockId()));
    }

    /**
     * Forks the chain and expands both chains.
     * It's verified that the Blockchain contains all blocks, but only the genesis is finalized.
     * Then, a single chain is expanded such that the other chain is discarded.
     * We verify that no block from the discarded chain figures in the {@link Blockchain},
     *  and that the blocks of the longest chain are finalized.
     */
    @Test
    void bigFork() throws Exception {
        UUID l1, l2;
        l1 = l2 = blockchain.getBlockR().iterator().next();
        for (int i = 0; i < 50; i++) {
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 =
                    createSimpleBlock(l1);
            blockchain.submitBlock(b1);
            l1 = b1.getBlockId();

            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 =
                    createSimpleBlock(l2);
            blockchain.submitBlock(b2);
            l2 = b2.getBlockId();
        }
        Thread.sleep(100);
        assertEquals(1, blockchain.getFinalizedIds().size());
        assertEquals(1 + 2 * 50, blockchain.getNodesIds().size());

        for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
            blockchain.submitBlock(createSimpleBlock(blockchain.getBlockR().iterator().next()));
            Thread.sleep(100);
        }
        assertEquals(50 + 1, blockchain.getFinalizedIds().size());
        assertEquals(50 + 1 + blockchain.getFinalizedWeight(), blockchain.getNodesIds().size());
    }

    /**
     * Creates several forks in the chain.
     * This is done by submitting pairs of forking blocks.
     * Verifies that the blocks are discarded and finalized exactly when it is expected.
     */
    @Test
    void manySmallForks() throws Exception {
        for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
            Set<UUID> last = blockchain.getBlockR();
            blockchain.submitBlock(createSimpleBlock(last.iterator().next()));
            blockchain.submitBlock(createSimpleBlock(last.iterator().next()));
            Thread.sleep(10);
        }
        assertEquals(1 + 2 * blockchain.getFinalizedWeight(), blockchain.getNodesIds().size());
        assertEquals(1, blockchain.getFinalizedIds().size());

        for (int i = 0; i < 20; i++) {
            Set<UUID> last = blockchain.getBlockR();
            blockchain.submitBlock(createSimpleBlock(last.iterator().next()));
            blockchain.submitBlock(createSimpleBlock(last.iterator().next()));
            Thread.sleep(10);
            assertEquals(i + 2, blockchain.getFinalizedIds().size());
        }
    }

    /**
     * Submits two blocks in the wrong order.
     * This is, block B2 that references block B1 is submitted first.
     * Verifies that the {@link Blockchain} is able to reorder the blocks and contains both.
     */
    @Test
    void testSubmitWrongOrder() throws Exception {
        Set<UUID> blocks = new HashSet<>(3);
        UUID genesis = blockchain.getBlockR().iterator().next();
        blocks.add(genesis);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 =
                createSimpleBlock(genesis);
        blocks.add(b1.getBlockId());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 =
                createSimpleBlock(b1.getBlockId());
        blocks.add(b2.getBlockId());
        blockchain.submitBlock(b2);
        blockchain.submitBlock(b1);
        Thread.sleep(100);
        assertEquals(blocks, blockchain.getNodesIds());
    }

    /**
     * Submits several blocks in the wrong order.
     * Verifies if all blocks are eventually accepted by the {@link Blockchain} and correctly reordered.
     */
    @Test
    void testSeveralWrongOrder() throws Exception {
        Set<UUID> blocks = new HashSet<>(11);
        List<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> reversedBlocks = new LinkedList<>();
        UUID genesis = blockchain.getBlockR().iterator().next();
        blocks.add(genesis);
        UUID last = genesis;
        for (int i = 0; i < 10; i++) {
            LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                    createSimpleBlock(last);
            blocks.add(block.getBlockId());
            reversedBlocks.add(0, block);
            last = block.getBlockId();
        }
        reversedBlocks.forEach(blockchain::submitBlock);
        Thread.sleep(150);
        assertEquals(blocks, blockchain.getNodesIds());
    }

    /**
     * Submits two blocks in the wrong order, such that one is received far before the second.
     * Its expected that the first received block is discarded.
     */
    @Test
    void testDiscardOrphanBlock() throws Exception {
        Set<UUID> blocks = new HashSet<>(2);
        UUID genesis = blockchain.getBlockR().iterator().next();
        blocks.add(genesis);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 =
                createSimpleBlock(genesis);
        blocks.add(b1.getBlockId());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 =
                createSimpleBlock(b1.getBlockId());
        blocks.add(b2.getBlockId());
        blockchain.submitBlock(b2);
        Thread.sleep(1100);
        blockchain.submitBlock(b1);
        Thread.sleep(10);
        assertEquals(blocks, blockchain.getNodesIds());
    }

    @Test
    void shouldFindInLongestChain() throws Exception {
        UUID genesis = blockchain.getBlockR().iterator().next();
        assertTrue(blockchain.isInLongestChain(genesis));
        blockchain.submitBlock(createSimpleBlock(genesis));
        Thread.sleep(100);
        assertTrue(blockchain.isInLongestChain(genesis));
        for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
            blockchain.submitBlock(createSimpleBlock(blockchain.getBlockR().iterator().next()));
            Thread.sleep(100);
        }
        assertTrue(blockchain.isInLongestChain(genesis));
    }

    @Test
    void shouldFindInLongestChainWhenTied() throws Exception {
        var block1 = createSimpleBlock(blockchain.getBlockR().iterator().next());
        var block2 = createSimpleBlock(blockchain.getBlockR().iterator().next());
        blockchain.submitBlock(block1);
        blockchain.submitBlock(block2);
        Thread.sleep(1000);
        assertTrue(blockchain.isInLongestChain(block1.getBlockId()));
        assertTrue(blockchain.isInLongestChain(block2.getBlockId()));
        System.out.println("Something");
    }

    @Test
    void shouldNotFindInLongestChain() throws Exception {
        var block1 = createSimpleBlock(blockchain.getBlockR().iterator().next());
        var block2 = createSimpleBlock(blockchain.getBlockR().iterator().next());
        blockchain.submitBlock(block1);
        blockchain.submitBlock(block2);
        Thread.sleep(100);
        blockchain.submitBlock(createSimpleBlock(block2.getBlockId()));
        Thread.sleep(100);
        assertFalse(blockchain.isInLongestChain(block1.getBlockId()));
    }

    private LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> createSimpleBlock(UUID previous) throws Exception {
        return new LedgerBlockImp<>(1, List.of(previous),
                new SimpleBlockContentList<>(Collections.emptyList()), dwait.compileDRandProof(), proposer);
    }

}
