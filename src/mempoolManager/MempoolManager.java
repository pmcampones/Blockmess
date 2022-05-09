package mempoolManager;

import broadcastProtocols.BroadcastValue;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import ledger.AppContent;
import ledger.blocks.ContentList;
import ledger.blocks.LedgerBlock;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.stream.Collectors.toList;

public class MempoolManager extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(MempoolManager.class);

    public static final short ID = IDGenerator.genId();

    private final ReadWriteLock mempoolLock = new ReentrantReadWriteLock();

    /**The complete collection of unused UTXOs in the finalized portion of the system**/
    public final Map<UUID, MempoolChunk> mempool = new ConcurrentHashMap<>();

    /**
     * Contains the file the relative path to the file where the contents will be placed.
     * <p>This field is empty if this node is not recording the blocks.</p>
     */
    private final MinimalistRecordModule recordModule;

    private static MempoolManager singleton;

    private MempoolManager() throws Exception {
        super(MempoolManager.class.getSimpleName(), ID);
        this.recordModule = new MinimalistRecordModule();
        bootstrapDL();
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<LedgerBlock> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification(notif));
        BroadcastValue.pojoSerializers.put(ContentList.ID, ContentList.serializer);
    }

    private void bootstrapDL() {
        logger.info("Bootstrapping DL");
        List<MempoolChunk> chunks = BootstrapModule.getStoredChunks();
        logger.info("Successfully bootstrapped {} blocks.", chunks.size());
    }

    public static MempoolManager getSingleton() {
        if (singleton == null) {
            try {
                singleton = new MempoolManager();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return singleton;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<LedgerBlock> notif) {
        LedgerBlock block = notif.getNonFinalizedBlock();
        logger.debug("Received non finalized block with id {}", block.getBlockId());
        MempoolChunk chunk = createChunk(block);
        mempool.put(chunk.getId(), chunk);
    }

    private MempoolChunk createChunk(LedgerBlock block) {
        List<AppContent> unwrappedContent = Collections.emptyList();
        return new MempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()), unwrappedContent);
    }

    private void uponDeliverFinalizedBlockNotification(DeliverFinalizedBlockIdentifiersNotification notif) {
        notif.getDiscardedBlockIds().forEach(mempool::remove);
        finalize(notif.getFinalizedBlocksIds());
    }

    public void finalize(List<UUID> finalized) {
        List<MempoolChunk> finalizedChunks = finalized.stream().map(mempool::get).collect(toList());
        recordBlocks(finalizedChunks);
        finalizeBlocks(finalizedChunks);
    }

    private void recordBlocks(List<MempoolChunk> finalized) {
        try {
            recordModule.recordBlocks(finalized);
        } catch (IOException e) {
            logger.error("Unable to write to file because: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void finalizeBlocks(List<MempoolChunk> finalized) {
        try {
            mempoolLock.writeLock().lock();
            tryToFinalizeBlocks(finalized);
        } finally {
            mempoolLock.writeLock().unlock();
        }
    }

    private void tryToFinalizeBlocks(List<MempoolChunk> finalized) {
        triggerNotification(new DeliverFinalizedBlocksContentNotification());
        finalized.stream().map(MempoolChunk::getId).forEach(mempool::remove);
    }

    public Set<UUID> getUsedContentFromChunk(UUID previousState, Set<UUID> visited) {
        MempoolChunk chunk = mempool.get(previousState);
        if (visited.contains(previousState) || chunk == null) return Collections.emptySet();
        visited.add(previousState);
        Set<UUID> invalid = new HashSet<>(chunk.getUsedIds());
        chunk.getPreviousChunksIds().forEach(id -> invalid.addAll(getUsedContentFromChunk(id, visited)));
        return invalid;
    }

}
