import applicationInterface.GlobalProperties;
import applicationInterface.launcher.LauncherCommon;
import broadcastProtocols.BroadcastValue;
import cmux.AppOperation;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.blocks.ValidatorSignature;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.nodes.BlockmessChain;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;
import validators.ApplicationObliviousValidator;
import validators.FixedApplicationObliviousValidator;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

import static java.lang.Integer.parseInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LedgerManagerTests {

    static class LedgerManagerObserver implements LedgerObserver {

        @Getter
        private final Map<UUID, BlockmessBlock> blocks = new HashMap<>();

        @Getter
        private final List<UUID> finalized = new ArrayList<>();

        @Getter
        private final Set<UUID> discarded = new HashSet<>();

        @Override
        public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
            blocks.put(block.getBlockId(), block);
        }

        @Override
        public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
            this.finalized.addAll(finalized);
            this.discarded.addAll(discarded);
        }
    }

    static class DummyBlockmessBlock implements BlockmessBlock {

        @Getter
        private final UUID blockId;

        @Getter
        private final UUID destinationChain;

        @Getter
        private final List<UUID> prevRefs;

        @Getter
        private final ContentList contentList;

        @Getter
        private final long blockRank;

        @Getter
        private final long nextRank;

        @Getter
        private final PublicKey proposer;
        
        DummyBlockmessBlock(UUID blockId, UUID destinationChain, List<UUID> prevRefs, ContentList contentList, long blockRank, long nextRank, PublicKey proposer) {
            this.blockId = blockId;
            this.destinationChain = destinationChain;
            this.prevRefs = prevRefs;
            this.contentList = contentList;
            this.blockRank = blockRank;
            this.nextRank = nextRank;
            this.proposer = proposer;
        }

        @Override
        public short getClassId() {
            return 0;
        }

        @Override
        public ISerializer<BroadcastValue> getSerializer() {
            return null;
        }

        @Override
        public boolean isBlocking() {
            return false;
        }

        @Override
        public UUID getBlockingID() {
            return null;
        }

        @Override
        public int getInherentWeight() {
            return 1;
        }

        @Override
        public SybilResistantElectionProof getProof() {
            return null;
        }

        @Override
        public List<ValidatorSignature> getSignatures() {
            return Collections.emptyList();
        }

        @Override
        public void addValidatorSignature(ValidatorSignature validatorSignature) {

        }

        @Override
        public int getSerializedSize() throws IOException {
            return 0;
        }
    }

    static class DummyApplicationObliviousValidator implements ApplicationObliviousValidator {

        @Override
        public boolean isBlockValid(BlockmessBlock block) {
            return true;
        }

        @Override
        public boolean isProofValid(BlockmessBlock block) {
            return true;
        }
    }

    private final KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();

    Properties props = Babel.loadConfig(new String[]{"initialNumChains=1", "minNumChains=1", "maxNumChains=999"}, LauncherCommon.DEFAULT_CONF);

    public LedgerManagerTests() throws InvalidParameterException, IOException {
        GlobalProperties.setProps(props);
        FixedApplicationObliviousValidator.getSingleton().setCustomValidator(new DummyApplicationObliviousValidator());
    }

    @Test
    void shouldHaveTheGenesisBlock() {
        Set<UUID> prevs = LedgerManager.getSingleton().getBlockR();
        assertEquals(1, prevs.size());
    }

    @Test
    void shouldHaveNoDeliveredBlocks() {
        LedgerManager ledgerManager = LedgerManager.getSingleton();
        LedgerManagerObserver ledgerManagerObserver = new LedgerManagerObserver();
        ledgerManager.attachObserver(ledgerManagerObserver);
        assertTrue(ledgerManagerObserver.getFinalized().isEmpty());
    }

    @Test
    void shouldDeliverOneWithSequentialRanksBlock() {
        LedgerManager ledgerManager = LedgerManager.getSingleton();
        LedgerManagerObserver ledgerManagerObserver = new LedgerManagerObserver();
        ledgerManager.attachObserver(ledgerManagerObserver);
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), i, i+1));
        }
        assertEquals(1, ledgerManagerObserver.getFinalized().size());
    }

    @Test
    void shouldDeliverOneWithJumpingRanksBlock() {
        LedgerManager ledgerManager = LedgerManager.getSingleton();
        LedgerManagerObserver ledgerManagerObserver = new LedgerManagerObserver();
        ledgerManager.attachObserver(ledgerManagerObserver);
        int currRank = 0;
        int nextRank = 1 + new Random().nextInt(10);
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++) {
            ledgerManager.submitBlock(genSmallBlock(ledgerManager.getOrigin(), currRank, nextRank));
            currRank = nextRank;
            nextRank = currRank + 1 + new Random().nextInt(10);
        }
        assertEquals(1, ledgerManagerObserver.getFinalized().size());
    }

    /*@Test
    void shouldCreateChainAndStopDelivering() {
        LedgerManager ledgerManager = LedgerManager.getSingleton();
        LedgerManagerObserver ledgerManagerObserver = new LedgerManagerObserver();
        ledgerManager.attachObserver(ledgerManagerObserver);
        int currRank = 0;
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++)
            ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
        assertEquals(1, ledgerManagerObserver.getFinalized().size());
        //ledgerManager.getOrigin().spawnChildren(ledgerManager.getOrigin().getBlockR().iterator().next());
        assertEquals(1, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 3; i++)
            ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
        //ledgerManager.deliverFinalizedBlocksAsync();
        assertEquals(ledgerManager.getFinalizedWeight() + 4, ledgerManagerObserver.getFinalized().size());
        assertEquals(3, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++)
            ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
        assertEquals(2 * ledgerManager.getFinalizedWeight() + 4, ledgerManagerObserver.getFinalized().size());
        assertEquals(3, ledgerManager.getChains().size());
        for (int i = 0; i < ledgerManager.getFinalizedWeight() + 1; i++)
            ledgerManager.submitBlock(genLargeBlock(ledgerManager.getOrigin(), currRank, ++currRank));
        assertEquals(2 * ledgerManager.getFinalizedWeight() + 4, ledgerManagerObserver.getFinalized().size());
        assertEquals(3, ledgerManager.getChains().size());
        //return ledgerManagerObserver.getFinalized().size();
    }*/

    private BlockmessBlock genSmallBlock(BlockmessChain origin, int currRank, int nextRank) {
        List<UUID> prevRefs = List.copyOf(origin.getBlockR());
        UUID chainId = origin.getChainId();
        ContentList contentList = new ContentList(Collections.emptyList());
        return new DummyBlockmessBlock(UUID.randomUUID(), chainId, prevRefs, contentList, currRank, nextRank, proposer.getPublic());
    }

    private BlockmessBlock genLargeBlock(BlockmessChain origin, int currRank, int nextRank) {
        int maxBlockSize = parseInt(props.getProperty("maxBlockSize", "21000"));
        List<UUID> prevRefs = List.copyOf(origin.getBlockR());
        UUID chainId = origin.getChainId();
        AppOperation appOperation = new AppOperation(new byte[maxBlockSize], new byte[0]);
        ContentList contentList = new ContentList(List.of(appOperation));
        return new DummyBlockmessBlock(UUID.randomUUID(), chainId, prevRefs, contentList, currRank, nextRank, proposer.getPublic());
    }


}
