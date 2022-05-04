package test.java;

import catecoin.blockConstructors.BlockDirector;
import catecoin.blockConstructors.BlockDirectorKits;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.blocks.ValidatorSignature;
import catecoin.blocks.ValidatorSignatureImp;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.mempoolManager.MempoolManager;
import catecoin.mempoolManager.MempoolManagerFactory;
import catecoin.mempoolManager.MinimalistBootstrapModule;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import catecoin.validators.ContextObliviousValidator;
import catecoin.validators.PoETProofValidator;
import ledger.Ledger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.security.KeyPair;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link ContextObliviousValidator} class.
 * These tests consist on the submission of several blocks, most incorrect,
 *  and evaluate if the class is able to detect inconsistencies.
 */
public class ContextObliviousValidatorTests {

    private final KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();

    private final KeyPair txSender = CryptographicUtils.generateECDSAKeyPair();

    private final KeyPair txReceiver = CryptographicUtils.generateECDSAKeyPair();

    private final Properties props = Babel.loadConfig(new String[] {}, Main.DEFAULT_TEST_CONF);

    private final MempoolManager<SlimTransaction, PoETDRandProof> mempoolManager = MempoolManagerFactory.getMinimalistMempoolManager(props);

    private final ContextObliviousValidator<PoETDRandProof> appValidator =
            new ContextObliviousValidator<>(props, mempoolManager, new PoETProofValidator(props));

    private final Ledger<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> ledger =
            new Blockchain<>(props, appValidator, new MinimalistBootstrapModule());

    private final BlockDirector<SlimTransaction, BlockContent<SlimTransaction>, LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>, PoETDRandProof> bc =
            BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, proposer);

    private final PoETWithDRand<BlockContent<SlimTransaction>> dwait =
            new PoETWithDRand<>(props, proposer, ledger, bc);

    public ContextObliviousValidatorTests() throws Exception {}

    /**
     * Submits an empty block with the correct proof and signatures and verifies if it is accepted.
     */
    @Test
    void testValidEmptyBlock() throws Exception {
        PoETDRandProof proof = dwait.compileDRandProof();
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(emptyList());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                new LedgerBlockImp<>(1, List.of(randomUUID()), blockContent, proof, proposer);
        assertTrue(appValidator.receivedValid(block));
    }

    /**
     * Records an input into the {@link ContextObliviousValidator} and generates a transaction using this input.
     * Verifies if the correct block containing this transaction is accepted.
     */
    @Test
    void testValidBlockWithRegularInput() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        //mempoolManager.utxos.put(input.getId(), input);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(input.getId(), input.getAmount(), List.of(randomUUID()));
        assertTrue(appValidator.receivedValid(block));
    }

    /**
     * Attempts to submit a block with an input from another user.
     */
    @Test
    void testInputFromAnotherUser() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txReceiver.getPublic());
        //mempoolManager.utxos.put(input.getId(), input);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(input.getId(), input.getAmount(), List.of(randomUUID()));
        assertFalse(appValidator.receivedValid(block));
    }

    /**
     * Attempts to submit a block that is signed by someone other than the block proposer.
     */
    @Test
    void testSignedUnexpectedNode() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        //mempoolManager.utxos.put(input.getId(), input);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(input.getId(),input.getAmount(), List.of(randomUUID()));
        List<ValidatorSignature> blockSignatures = block.getSignatures();
        blockSignatures.remove(0);
        blockSignatures.add(new ValidatorSignatureImp(txReceiver, block.getBlockId()));
        assertFalse(appValidator.receivedValid(block));
    }

    /**
     * Attempts to submit a block where the amount of coins in the input does not equal the amount of coins in the output UTXOs.
     */
    @Test
    void testInputAndOutputAmountsDontMatch() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        //mempoolManager.utxos.put(input.getId(), input);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(input.getId(),input.getAmount() - 1, List.of(randomUUID()));
        assertFalse(appValidator.receivedValid(block));
    }

    @Test
    void testAverageBlock() throws Exception {
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = getBlockNumTransactions(15);
        assertTrue(appValidator.receivedValid(block));
    }

    /**
     * Generates an exceedingly large block and attempts to submit it.
     */
    @Test
    void testOversizeBlock() throws Exception {
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = getBlockNumTransactions(5000);
        assertFalse(appValidator.receivedValid(block));
    }

    LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> getBlockNumTransactions(int numTxs) throws Exception {
        List<SlimTransaction> manyTxs = generateValidTransactions(numTxs);
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(manyTxs);
        PoETDRandProof proof = dwait.compileDRandProof();
        return new LedgerBlockImp<>(1, List.of(randomUUID()), blockContent, proof, proposer);
    }

    private List<SlimTransaction> generateValidTransactions(int amount) throws Exception {
        List<SlimTransaction> txs = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            StorageUTXO input = new StorageUTXO(randomUUID(), new Random().nextInt(100) + 1, txSender.getPublic());
            //mempoolManager.utxos.put(input.getId(), input);
            txs.add(new SlimTransaction(txSender.getPublic(), txReceiver.getPublic(),
                    List.of(input.getId()), List.of(input.getAmount()),
                    emptyList(), txSender.getPrivate()));
        }
        return txs;
    }

    /**
     * Records and input in the {@link ContextObliviousValidator};
     *  then marks the input as consumed in a {@link MinimalistMempoolChunk} (transition states between finalized blocks).
     * Submits a block using the consumed input and is invalidated.
     */
    @Test
    void testInputInvalidInChunk() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        UUID inputId = input.getId();
        //mempoolManager.utxos.put(inputId, input);
        MinimalistMempoolChunk chunk = new MinimalistMempoolChunk(randomUUID(), emptySet(),
                emptySet(), Set.of(inputId), emptySet(), 1);
        mempoolManager.mempool.put(chunk.getId(), chunk);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(inputId, input.getAmount(), List.of(chunk.getId()));
        assertFalse(appValidator.receivedValid(block));
    }

    /**
     * Generates a block that uses an input that is not part of the finalized state of the application, but is in one {@link MinimalistMempoolChunk}
     * Verifies that the input is considered and the block is valid.
     */
    @Test
    void testInputInChunk() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        MinimalistMempoolChunk chunk = new MinimalistMempoolChunk(randomUUID(), emptySet(),
                Set.of(input), emptySet(), emptySet(), 1);
        mempoolManager.mempool.put(chunk.getId(), chunk);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(input.getId(), input.getAmount(), List.of(chunk.getId()));
        assertTrue(appValidator.receivedValid(block));
    }

    /**
     *       /   C1  \       /   C4
     *   C0              C3
     *       \   C2  /       \   C5
     *   Input in C0 and invalidated in C5
     *
     * The figure above represents the {@link MinimalistMempoolChunk}s in the {@link ContextObliviousValidator} at a given point, and their connections.
     * An input is recorded in chunk C0 and then invalidated by chunk C5.
     * Verifies that transactions following a view with C0 but not C5 are valid;
     *  while those following a view with C0 and C5 are invalid.
     **/
    @Test
    void testValidInputInPath() throws Exception {
        StorageUTXO input = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        MinimalistMempoolChunk c0 = new MinimalistMempoolChunk(randomUUID(), emptySet(),
                Set.of(input), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c1 = new MinimalistMempoolChunk(randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c2 = new MinimalistMempoolChunk(randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c3 = new MinimalistMempoolChunk(randomUUID(), Set.of(c1.getId(), c2.getId()),
                emptySet(), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c4 = new MinimalistMempoolChunk(randomUUID(), Set.of(c3.getId()),
                emptySet(), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c5 = new MinimalistMempoolChunk(randomUUID(), Set.of(c3.getId()),
                emptySet(), Set.of(input.getId()), emptySet(), 1);
        mempoolManager.mempool.put(c0.getId(), c0);
        mempoolManager.mempool.put(c1.getId(), c1);
        mempoolManager.mempool.put(c2.getId(), c2);
        mempoolManager.mempool.put(c3.getId(), c3);
        mempoolManager.mempool.put(c4.getId(), c4);
        mempoolManager.mempool.put(c5.getId(), c5);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> validBlock =
                getBlockWithTransaction(input.getId(), input.getAmount(), List.of(c4.getId()));
        assertTrue(appValidator.receivedValid(validBlock));
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> invalidBlock =
                getBlockWithTransaction(input.getId(), input.getAmount(), List.of(c4.getId(), c5.getId()));
        assertFalse(appValidator.receivedValid(invalidBlock));
    }

    /**
     * Creates a {@link MinimalistMempoolChunk} through the correct means (unlike the other tests where chunks are allocated directly into the DS).
     * Verifies the state of the mempool is the expected.
     */
    @Test
    void testDeliverUnfinalizedValidBlock() throws Exception {
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block =
                getBlockWithTransaction(randomUUID(),10, List.of(randomUUID()));
        MempoolChunk chunk = mempoolManager.buildMempoolChunk(block, -1);
        mempoolManager.mempool.put(chunk.getId(), chunk);
        assertEquals(1, chunk.getAddedUtxos().size());
        assertEquals(block.getBlockContent().getContentList().get(0).getOutputsDestination().get(0).getId(),
                chunk.getAddedUtxos().iterator().next().getId());
    }

    /**
     *   C0 -> C1
     *
     * Finalises one block corresponding to {@link MinimalistMempoolChunk}s C0.
     * Verifies that the permanent state of the {@link ContextObliviousValidator} contains the UTXO in the finalized block,
     *  and the mempool accounts for the finalization of the block.
     **/
    @Test
    void testDeliverFinalizedBlock() throws Exception {
        //int initialUtxos = mempoolManager.utxos.size();
        StorageUTXO i0 = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        StorageUTXO i1 = new StorageUTXO(randomUUID(), 10, txSender.getPublic());
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = getBlockWithTransaction(i0.getId(), i0.getAmount(), List.of(randomUUID()));
        MinimalistMempoolChunk c0 = new MinimalistMempoolChunk(block.getBlockId(), emptySet(),
                Set.of(i0), emptySet(), emptySet(), 1);
        MinimalistMempoolChunk c1 = new MinimalistMempoolChunk(randomUUID(), Set.of(c0.getId()),
                Set.of(i1), emptySet(), emptySet(), 1);
        mempoolManager.mempool.put(c0.getId(), c0);
        mempoolManager.mempool.put(c1.getId(), c1);
        assertEquals(2, mempoolManager.mempool.size());
        mempoolManager.finalize(List.of(block.getBlockId()));
        assertEquals(1, mempoolManager.mempool.size());
        //assertEquals(initialUtxos + 1, mempoolManager.utxos.size());
    }

    private LedgerBlock<BlockContent<SlimTransaction>,PoETDRandProof> getBlockWithTransaction(
            UUID input, int amount, List<UUID> previous) throws Exception {
        SlimTransaction tx = new SlimTransaction(txSender.getPublic(), txReceiver.getPublic(),
                List.of(input), List.of(amount), emptyList(),
                txSender.getPrivate());
        BlockContent<SlimTransaction> bc = new SimpleBlockContentList<>(List.of(tx));
        PoETDRandProof proof = dwait.compileDRandProof();
        return new LedgerBlockImp<>(1, previous, bc, proof, proposer);
    }

}
