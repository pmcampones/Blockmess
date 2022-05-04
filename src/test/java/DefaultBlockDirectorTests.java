package test.java;

import catecoin.blockConstructors.BlockDirector;
import catecoin.blockConstructors.BlockDirectorKits;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.mempoolManager.MempoolManager;
import catecoin.mempoolManager.MempoolManagerFactory;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;

import java.io.IOException;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;

import static catecoin.blockConstructors.AbstractContentStorage.MAX_BLOCK_SIZE;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultBlockDirectorTests {

    private final KeyPair myKeys = CryptographicUtils.generateECDSAKeyPair();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private final MempoolManager<SlimTransaction,PoETDRandProof> mempoolManager = MempoolManagerFactory.getMinimalistMempoolManager(props);

    private final BlockDirector<SlimTransaction, BlockContent<SlimTransaction>, LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>, PoETDRandProof> bc =
            BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, myKeys);

    public DefaultBlockDirectorTests() throws Exception {}

    @Test
    void testEmptyBlock() throws IOException {
        List<SlimTransaction> txs = bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE);
        assertTrue(txs.isEmpty());
    }

    @Test
    void testRegularBlock() throws Exception {
        Set<UUID> txsIds = new HashSet<>(10);
        for (int i = 0; i < 10; i++) {
            SlimTransaction tx = generateTransaction();
            txsIds.add(tx.getId());
            bc.submitContent(tx);
        }
        Thread.sleep(100);
        List<SlimTransaction> txs = bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE);
        assertEquals(10, txs.size());
        assertTrue(txsIds.containsAll(txs.stream()
                .map(SlimTransaction::getId)
                .collect(Collectors.toList())));
        assertTrue(txs.stream()
                .mapToInt(SlimTransaction::getSerializedSize).sum()
                <= MAX_BLOCK_SIZE);
    }

    @Test
    void testTooManyTransactions() throws Exception {
        int numTxs = 3000;
        Set<UUID> txsIds = new HashSet<>(numTxs);
        for (int i = 0; i < numTxs; i++) {
            SlimTransaction tx = generateTransaction();
            txsIds.add(tx.getId());
            bc.submitContent(tx);
        }
        List<SlimTransaction> txs = bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE);
        System.out.println(txs.size());
        System.out.println(txs.stream().mapToInt(SlimTransaction::getSerializedSize).sum());
        assertTrue(txs.size() < numTxs);
        assertTrue(txsIds.containsAll(txs.stream()
                .map(SlimTransaction::getId)
                .collect(Collectors.toList())));
        assertTrue(txs.stream()
                .mapToInt(SlimTransaction::getSerializedSize)
                .sum()
                <= MAX_BLOCK_SIZE);
    }

    @Test
    void testUsedTransaction() throws Exception {
        SlimTransaction used = generateTransaction();
        UUID stateId = UUID.randomUUID();
        MinimalistMempoolChunk chunk = new MinimalistMempoolChunk(stateId, emptySet(),
                emptySet(), emptySet(), Set.of(used.getId()), 1);
        bc.submitContent(used);
        mempoolManager.mempool.put(chunk.getId(), chunk);
        List<SlimTransaction> txs = bc.generateBoundBlockContentList(
                List.of(chunk.getId()), 0, Integer.MAX_VALUE);
        assertTrue(txs.isEmpty());
    }

    @Test
    void testUsedInDifferentFork() throws Exception {
        SlimTransaction used = generateTransaction();
        UUID stateId = UUID.randomUUID();
        MinimalistMempoolChunk chunk = new MinimalistMempoolChunk(stateId, emptySet(),
                emptySet(), emptySet(), Set.of(used.getId()), 1);
        bc.submitContent(used);
        mempoolManager.mempool.put(chunk.getId(), chunk);
        List<SlimTransaction> txs = bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE);
        assertEquals(1, txs.size());
        assertEquals(used.getId(), txs.get(0).getId());
    }

    /*
                /   C1
            C0
                \   C2  -   C3
     */
    @Test
    void testUsedInDifferentChunks() throws Exception {
        SlimTransaction c0tx = generateTransaction();
        SlimTransaction c1tx = generateTransaction();
        SlimTransaction c2tx = generateTransaction();
        SlimTransaction c3tx = generateTransaction();
        bc.submitContent(c0tx);
        bc.submitContent(c1tx);
        bc.submitContent(c2tx);
        bc.submitContent(c3tx);
        MinimalistMempoolChunk c0 = new MinimalistMempoolChunk(UUID.randomUUID(), emptySet(),
                emptySet(), emptySet(), Set.of(c0tx.getId()), 1);
        MinimalistMempoolChunk c1 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), Set.of(c1tx.getId()), 1);
        MinimalistMempoolChunk c2 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), Set.of(c2tx.getId()), 1);
        MinimalistMempoolChunk c3 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c2.getId()),
                emptySet(), emptySet(), Set.of(c3tx.getId()), 1);
        mempoolManager.mempool.put(c0.getId(), c0);
        mempoolManager.mempool.put(c1.getId(), c1);
        mempoolManager.mempool.put(c2.getId(), c2);
        mempoolManager.mempool.put(c3.getId(), c3);
        assertEquals(4, bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE).size());
        assertEquals(3, bc.generateBoundBlockContentList(
                List.of(c0.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(2, bc.generateBoundBlockContentList(
                List.of(c1.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(2, bc.generateBoundBlockContentList(
                List.of(c2.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(1, bc.generateBoundBlockContentList(
                List.of(c3.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(1, bc.generateBoundBlockContentList(
                List.of(c2.getId(), c3.getId()), 0, Integer.MAX_VALUE).size());
        assertTrue(bc.generateBoundBlockContentList(
                List.of(c1.getId(), c3.getId()), 0, Integer.MAX_VALUE).isEmpty());
    }

    /*
                /   C1  \       /   C4
            C0              C3
                \   C2  /       \   C5
     */
    @Test
    void testUsedInDifferentLooping() throws Exception {
        SlimTransaction c0tx = generateTransaction();
        SlimTransaction c1tx = generateTransaction();
        SlimTransaction c2tx = generateTransaction();
        SlimTransaction c3tx = generateTransaction();
        SlimTransaction c4tx = generateTransaction();
        SlimTransaction c5tx = generateTransaction();
        bc.submitContent(c0tx);
        bc.submitContent(c1tx);
        bc.submitContent(c2tx);
        bc.submitContent(c3tx);
        bc.submitContent(c4tx);
        bc.submitContent(c5tx);
        MinimalistMempoolChunk c0 = new MinimalistMempoolChunk(UUID.randomUUID(), emptySet(),
                emptySet(), emptySet(), Set.of(c0tx.getId()), 1);
        MinimalistMempoolChunk c1 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), Set.of(c1tx.getId()), 1);
        MinimalistMempoolChunk c2 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c0.getId()),
                emptySet(), emptySet(), Set.of(c2tx.getId()), 1);
        MinimalistMempoolChunk c3 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c1.getId(), c2.getId()),
                emptySet(), emptySet(), Set.of(c3tx.getId()), 1);
        MinimalistMempoolChunk c4 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c3.getId()),
                emptySet(), emptySet(), Set.of(c4tx.getId()), 1);
        MinimalistMempoolChunk c5 = new MinimalistMempoolChunk(UUID.randomUUID(), Set.of(c3.getId()),
                emptySet(), emptySet(), Set.of(c5tx.getId()), 1);
        mempoolManager.mempool.put(c0.getId(), c0);
        mempoolManager.mempool.put(c1.getId(), c1);
        mempoolManager.mempool.put(c2.getId(), c2);
        mempoolManager.mempool.put(c3.getId(), c3);
        mempoolManager.mempool.put(c4.getId(), c4);
        mempoolManager.mempool.put(c5.getId(), c5);
        assertEquals(6, bc.generateBoundBlockContentList(
                Collections.emptyList(), 0, Integer.MAX_VALUE).size());
        assertEquals(5, bc.generateBoundBlockContentList(
                List.of(c0.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(4, bc.generateBoundBlockContentList(
                List.of(c1.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(4, bc.generateBoundBlockContentList(
                List.of(c2.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(2, bc.generateBoundBlockContentList(
                List.of(c3.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(1, bc.generateBoundBlockContentList(
                List.of(c4.getId()), 0, Integer.MAX_VALUE).size());
        assertEquals(1, bc.generateBoundBlockContentList(
                List.of(c5.getId()), 0, Integer.MAX_VALUE).size());
        assertTrue(bc.generateBoundBlockContentList(
                List.of(c4.getId(), c5.getId()), 0, Integer.MAX_VALUE).isEmpty());
    }



    private SlimTransaction generateTransaction() throws Exception {
        return new SlimTransaction(myKeys.getPublic(), myKeys.getPublic(),
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                List.of(10), List.of(10), myKeys.getPrivate());
    }
}
