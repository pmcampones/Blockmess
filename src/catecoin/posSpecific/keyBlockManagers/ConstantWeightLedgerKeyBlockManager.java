package catecoin.posSpecific.keyBlockManagers;

import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.txs.IndexableContent;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.CryptographicUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.pos.sortition.proofs.KeyBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.LargeSortitionProof;
import utils.IDGenerator;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static java.lang.Integer.parseInt;


/**
 * Stores the identifiers of key blocks in the system and maps them to their cumulative weights in the Ledger.
 * <p>This only works on Ledgers where the weight of a block is fixed
 * when it is appended (e.g. Nakamoto's Blockchain).</p>
 * <p>If a Ledger using the <a href=https://eprint.iacr.org/2013/881.pdf>GHOST</a> rule was used for instance,
 * it would be necessary to query the ledger on the block's weight at any given instant.</p>
 */
public class ConstantWeightLedgerKeyBlockManager<C extends BlockContent<IndexableContent>> extends GenericProtocol implements KeyBlockManager {

    private static final Logger logger = LogManager.getLogger(ConstantWeightLedgerKeyBlockManager.class);

    public static final short ID = IDGenerator.genId();

    private static final int FINALIZED_WEIGHT = 10000;

    private final Map<UUID, StorageKeyBlock> keyBlocks = new HashMap<>();

    private UUID heaviestBlock; //Initially genesis

    public ConstantWeightLedgerKeyBlockManager(Properties props) throws HandlerRegistrationException,
            IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        super(ConstantWeightLedgerKeyBlockManager.class.getSimpleName(), ID);
        heaviestBlock = computeGenesis(props);
        subscribeNotifications();
    }

    private UUID computeGenesis(Properties props) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        int finalizedWeight = parseInt(props.getProperty("finalizedWeight",
                String.valueOf(FINALIZED_WEIGHT)));
        String genesisUUIDStr = props.getProperty("genesisUUID",
                "00000000-0000-0000-0000-000000000000");
        UUID id = UUID.fromString(genesisUUIDStr);
        PublicKey original = CryptographicUtils.readECDSAPublicKey(props.getProperty("originalPublic"));
        keyBlocks.put(id, new StorageKeyBlock(original, finalizedWeight, id));
        return id;
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverFinalizedBlocksContentNotification.ID,
                this::uponDeliverFinalizedBlocksContentNotification);
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                this::uponDeliverNonFinalizedBlockNotification);
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public UUID getHeaviestKeyBlock() {
        return heaviestBlock;
    }

    @Override
    public int getBlockWeight(UUID keyBlock) throws KeyBlockDoesNotExistException {
        return getBlock(keyBlock).getCumulativeWeight();
    }

    @Override
    public PublicKey getBlockProposer(UUID keyBlock) throws KeyBlockDoesNotExistException {
        return getBlock(keyBlock).getProposer();
    }

    @Override
    public UUID getPrevReference(UUID keyBlock) throws KeyBlockDoesNotExistException {
        return getBlock(keyBlock).getPrevRef();
    }

    private StorageKeyBlock getBlock(UUID keyBlock) throws KeyBlockDoesNotExistException {
        StorageKeyBlock block = keyBlocks.get(keyBlock);
        if (block == null) {
            throw new KeyBlockDoesNotExistException(keyBlock);
        } else {
            return block;
        }
    }

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<LedgerBlock<C, LargeSortitionProof>> notif, short source) {
        LedgerBlock<C, LargeSortitionProof> block = notif.getNonFinalizedBlock();
        if (isKeyBlock(block)) {
            PublicKey proposer = block.getProposer();
            int weight = notif.getCumulativeWeight();
            UUID bid = block.getBlockId();
            UUID prevRef = block.getSybilElectionProof().getKeyBlockId();
            keyBlocks.put(bid, new StorageKeyBlock(proposer, weight, prevRef));
            logger.info("Received key block {}, with comulative weight {}, referencing {}",
                    bid, weight, block.getPrevRefs());
            if (weight > keyBlocks.get(heaviestBlock).getCumulativeWeight()) {
                logger.info("Placing {} as the heaviest key block.", bid);
                heaviestBlock = bid;
            }
        }
    }

    private boolean isKeyBlock(LedgerBlock<C, LargeSortitionProof> block) {
        LargeSortitionProof proof = block.getSybilElectionProof();
        return proof instanceof KeyBlockSortitionProof;
    }

    private void uponDeliverFinalizedBlocksContentNotification(
            DeliverFinalizedBlocksContentNotification notif, short source) {
        notif.getRemovedUtxo()
                .stream()
                .map(StorageUTXO::getId)
                .forEach(keyBlocks::remove);
    }
}
