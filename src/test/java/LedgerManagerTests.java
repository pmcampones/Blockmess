package test.java;

import catecoin.blockConstructors.ContentStoragePrototype;
import catecoin.blockConstructors.ContextAwareContentStorage;
import catecoin.blockConstructors.PrototypicalContentStorage;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.mempoolManager.MinimalistBootstrapModule;
import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import catecoin.validators.BlockValidator;
import catecoin.validators.TestBlockValidator;
import io.netty.buffer.ByteBuf;
import ledger.PrototypicalLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeAlreadyDefinedException;
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

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LedgerManagerTests {

    private final KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();

    private final BlockValidator<BlockmessBlock<BlockContent<IndexableContent>, FakeSybilElectionProof>> validator = new TestBlockValidator<>();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> ledgerManager;

    public LedgerManagerTests() throws Exception {
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
        ProtoPojo.pojoSerializers.put(FakeSybilElectionProof.ID, FakeSybilElectionProof.serializer);
        setUpPrototypeLedger();
        setUpPrototypeContentStorage();
        ledgerManager = new LedgerManager<>(props);
    }

    private void setUpPrototypeContentStorage() throws PrototypeAlreadyDefinedException {
        PrototypicalContentStorage<StructuredValue<SlimTransaction>> contentStorage =
                new ContextAwareContentStorage<>(props, null);
        ContentStoragePrototype.setPrototype(contentStorage);
    }

    private void setUpPrototypeLedger() throws PrototypeAlreadyDefinedException {
        PrototypicalLedger<BlockmessBlock<BlockContent<IndexableContent>,FakeSybilElectionProof>> protoLedger =
                new Blockchain<>(props, validator, new MinimalistBootstrapModule());
        LedgerPrototype.setPrototype(protoLedger);
    }

    @BeforeEach
    void setUp() throws PrototypeHasNotBeenDefinedException {
        ledgerManager = new LedgerManager<>(props);
    }

    @Test
    void shouldHaveTheGenesisBlock() {
        Set<UUID> prevs = ledgerManager.getBlockR();
        assertEquals(1, prevs.size());
    }

    @Test
    void shouldHaveNoDeliveredBlocks() {
        assertTrue(ledgerManager.getFinalizedIds().isEmpty());
    }

    @Test
    void shouldDeliverOneWithSequentialRanksBlock() throws Exception {
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), i, i+1));
            Thread.sleep(200);
        }
        assertEquals(1, ledgerManager.getFinalizedIds().size());
    }

    @Test
    void shouldDeliverOneWithJumpingRanksBlock() throws Exception {
        int currRank = 0;
        int nextRank = 1 + new Random().nextInt(10);
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, nextRank));
            currRank = nextRank;
            nextRank = currRank + 1 + new Random().nextInt(10);
            Thread.sleep(200);
        }
        assertEquals(1, ledgerManager.getFinalizedIds().size());
    }

    @Test
    int shouldCreateChainAndStopDelivering() throws Exception {
        int currRank = 0;
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(1, ledgerManager.getFinalizedIds().size());
        ledgerManager.getOrigin().spawnChildren(ledgerManager.getOrigin().getBlockR().iterator().next());
        assertEquals(1, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 3; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        //ledgerManager.deliverFinalizedBlocksAsync();
        assertEquals(ledgerManager.getFinalizedWeight() + 4, ledgerManager.getFinalizedIds().size());
        assertEquals(3, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(2 * ledgerManager.getFinalizedWeight() + 4, ledgerManager.getFinalizedIds().size());
        assertEquals(3, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(2 * ledgerManager.getFinalizedWeight() + 4, ledgerManager.getFinalizedIds().size());
        assertEquals(3, ledgerManager.getChains().size());
        return ledgerManager.getFinalizedIds().size();
    }

    @Test
    void shouldCreateAChainAndCatchUpConfirmBar() throws Exception {
        int numFinalized = shouldCreateChainAndStopDelivering();
        var itChains = ledgerManager.getChainIt();
        itChains.next();
        BlockmessChain lft = itChains.next();
        BlockmessChain rgt = itChains.next();
        for (int i = 0; i < 2 * (ledgerManager.getFinalizedWeight() + 1); i += 2) {
            ledgerManager.submitBlock(genSmallBlock(lft, 100 + i, 100 + i + 2));
            ledgerManager.submitBlock(genSmallBlock(rgt, 100 + i + 1, 100 + i + 3));
            Thread.sleep(200);
        }
        assertEquals(numFinalized + ledgerManager.getFinalizedWeight() + 2, ledgerManager.getFinalizedIds().size());
    }

    @Test
    void shouldSpawnWithOverloadedBlocks() throws Exception {
        assertEquals(1, ledgerManager.getChains().size());
        int currRank = 0;
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + ledgerManager.getNumSamples() / 2; i++) {
            ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(1, ledgerManager.getChains().size());
        assertFalse(ledgerManager.getOrigin().shouldSpawn());
        ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
        Thread.sleep(200);
        ledgerManager.deliverFinalizedBlocksAsync();
        Thread.sleep(200);
        assertEquals(3, ledgerManager.getChains().size());
    }

    @Test
    void shouldMergeWithUnderloadedBlocks() throws Exception {
        shouldSpawnWithOverloadedBlocks();
        int currRank = 100;
        for (int i = 0; i < 2 * ledgerManager.getFinalizedWeight(); i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(3, ledgerManager.getChains().size());
        for (int i = 0; i < 4 * ledgerManager.getFinalizedWeight(); i++) {
            for (var Chain : ledgerManager.getChains().values())
                Chain.submitBlock(genSmallBlock(Chain, currRank, ++currRank));
            Thread.sleep(200);
        }
        assertEquals(1, ledgerManager.getChains().size());
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> genSmallBlock(
            BlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> Chain, int currRank, int nextRank)
            throws Exception {
        return genBlock(Chain, currRank, nextRank, 1);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> genLargeBlock(
            BlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> Chain, int currRank, int nextRank)
            throws NoSuchAlgorithmException, SignatureException, IOException, InvalidKeyException {
        return genBlock(Chain, currRank, nextRank, ledgerManager.getMaxBlockSize() - 100);
    }

    private BlockmessBlock<BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> genBlock(
            BlockmessChain<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, FakeSybilElectionProof> Chain, int currRank, int nextRank, int size)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        List<UUID> prevRefs = List.copyOf(Chain.getBlockR());
        BlockContent<StructuredValue<SlimTransaction>> blockContent = new BlockContent<>() {
            @Override
            public boolean hasValidSemantics() {
                return false;
            }

            @Override
            public List<StructuredValue<SlimTransaction>> getContentList() {
                return emptyList();
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
                        byte[] salt = new byte[20];
                        new Random().nextBytes(salt);
                        byteBuf.writeBytes(salt);
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
        FakeSybilElectionProof proof = new FakeSybilElectionProof();
        return new BlockmessBlockImp<>(1, prevRefs, blockContent, proof, proposer,
                Chain.getChainId(), currRank, nextRank);
    }

}
