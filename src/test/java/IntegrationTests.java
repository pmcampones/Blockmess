package test.java;

import catecoin.blockConstructors.BlockDirector;
import catecoin.blockConstructors.BlockDirectorKits;
import catecoin.mempoolManager.MempoolManager;
import catecoin.mempoolManager.MempoolManagerFactory;
import catecoin.mempoolManager.MinimalistBootstrapModule;
import catecoin.transactionGenerators.TransactionGenerator;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import catecoin.validators.ContextObliviousValidator;
import catecoin.validators.PoETProofValidator;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;

import java.security.KeyPair;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.disjoint;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTests {

    private final KeyPair myKeys = CryptographicUtils.generateECDSAKeyPair();

    private final KeyPair other = CryptographicUtils.generateECDSAKeyPair();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private final MempoolManager<SlimTransaction, PoETDRandProof> mempoolManager = MempoolManagerFactory.getMinimalistMempoolManager(props);

    private final BlockDirector<SlimTransaction, BlockContent<SlimTransaction>, LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>, PoETDRandProof> blockCons =
            BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, myKeys);

    private final ContextObliviousValidator<PoETDRandProof> validator = new ContextObliviousValidator<>(props, mempoolManager,
            new PoETProofValidator(props));

    private final TransactionGenerator txGen = new TransactionGenerator(props, myKeys);

    private final Blockchain<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> blockchain =
            new Blockchain<>(props, validator, new MinimalistBootstrapModule());

    private final PoETWithDRand<BlockContent<SlimTransaction>> dwait =
            new PoETWithDRand<>(new Properties(), myKeys, blockchain, blockCons);

    public IntegrationTests() throws Exception {}

    @RepeatedTest(20)
    void testEmptyBlock() throws Exception {
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = generateBlock();
        assertTrue(validator.receivedValid(block));
        blockchain.submitBlock(block);
        Thread.sleep(300);
        assertTrue(blockchain.blocks.containsKey(block.getBlockId()));
    }

    @RepeatedTest(20)
    void testSingleTxBlock() throws Exception {
        StorageUTXO input = new StorageUTXO(UUID.randomUUID(), 100, myKeys.getPublic());
        //mempoolManager.utxos.put(input.getId(), input);
        txGen.myUTXOs.put(input.getId(), input.getAmount());
        SlimTransaction tx = txGen.generateTransaction(other.getPublic(), 10);
        blockCons.submitContent(tx);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> block = generateBlock();
        assertEquals(1, block.getBlockContent().getContentList().size());
        assertTrue(validator.receivedValid(block));
        blockchain.submitBlock(block);
        Thread.sleep(1000);
        assertTrue(blockchain.blocks.containsKey(block.getBlockId()));
    }

    @Test
    void testOverAbundanceOfTransactions() throws Exception {
        for (int i = 0; i < 3000; i++) {
            KeyPair sender = CryptographicUtils.generateECDSAKeyPair();
            KeyPair receiver = CryptographicUtils.generateECDSAKeyPair();
            TransactionGenerator senderGenerator = new TransactionGenerator(props, sender);
            StorageUTXO input = new StorageUTXO(UUID.randomUUID(), 100, sender.getPublic());
            //mempoolManager.utxos.put(input.getId(), input);
            senderGenerator.myUTXOs.put(input.getId(), input.getAmount());
            SlimTransaction tx = senderGenerator.generateTransaction(receiver.getPublic(), 50);
            blockCons.submitContent(tx);
        }
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b1 = generateBlock();
        blockchain.submitBlock(b1);
        mempoolManager.mempool.put(b1.getBlockId(), mempoolManager.buildMempoolChunk(b1, -1));
        System.out.println(b1.getSerializedSize());
        Thread.sleep(5000);
        LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> b2 = generateBlock();
        blockchain.submitBlock(b2);
        mempoolManager.mempool.put(b2.getBlockId(), mempoolManager.buildMempoolChunk(b2, -1));
        System.out.println(b2.getSerializedSize());
        Thread.sleep(5000);
        assertEquals(3, blockchain.blocks.size());

        Set<UUID> b1Txs = b1.getBlockContent().getContentList().stream().map(SlimTransaction::getId).collect(toSet());
        Set<UUID> b2Txs = b2.getBlockContent().getContentList().stream().map(SlimTransaction::getId).collect(toSet());
        assertTrue(disjoint(b1Txs, b2Txs));
    }

    private LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof> generateBlock() throws Exception {
        Set<UUID> previous = blockchain.getBlockR();
        PoETDRandProof proof = dwait.compileDRandProof();
        return blockCons.createBoundBlockProposal(previous, proof, Integer.MAX_VALUE);
    }
}
