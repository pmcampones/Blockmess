package mempoolManager;

import broadcastProtocols.BroadcastValue;
import ledger.AppContent;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.blocks.LedgerBlock;
import ledger.ledgerManager.LedgerManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import utils.IDGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MempoolManager extends GenericProtocol implements LedgerObserver {

    private static final Logger logger = LogManager.getLogger(MempoolManager.class);

    public static final short ID = IDGenerator.genId();

    /**The complete collection of unused UTXOs in the finalized portion of the system**/
    public final Map<UUID, MempoolChunk> mempool = new ConcurrentHashMap<>();

    private final List<LedgerObserver> observers = new LinkedList<>();

    private static MempoolManager singleton;

    private MempoolManager() {
        super(MempoolManager.class.getSimpleName(), ID);
        LedgerManager.getSingleton().attachObserver(this);
        bootstrapDL();
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
    public void init(Properties properties) {}

    public void attachObserver(LedgerObserver observer) {
        observers.add(observer);
    }

    private MempoolChunk createChunk(LedgerBlock block) {
        List<AppContent> unwrappedContent = Collections.emptyList();
        return new MempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()), unwrappedContent);
    }

    public Set<UUID> getUsedContentFromChunk(UUID previousState, Set<UUID> visited) {
        MempoolChunk chunk = mempool.get(previousState);
        if (visited.contains(previousState) || chunk == null) return Collections.emptySet();
        visited.add(previousState);
        Set<UUID> invalid = new HashSet<>(chunk.getUsedIds());
        chunk.getPreviousChunksIds().forEach(id -> invalid.addAll(getUsedContentFromChunk(id, visited)));
        return invalid;
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
        logger.debug("Received non finalized block with id {}", block.getBlockId());
        MempoolChunk chunk = createChunk(block);
        mempool.put(chunk.getId(), chunk);
        observers.forEach(observer -> deliverNonFinalizedBlock(block, weight));
    }

    @Override
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        discarded.forEach(mempool::remove);
        List<MempoolChunk> finalizedChunks = finalized.stream().map(mempool::get).collect(toList());
        finalizedChunks.stream().map(MempoolChunk::getId).forEach(mempool::remove);
        List<AppContent> finalizedContent = finalizedChunks.stream()
                .map(MempoolChunk::getAddedContent)
                .flatMap(Collection::stream)
                .collect(toList());
        LedgerManager.getSingleton().deleteContent(finalizedContent.stream().map(AppContent::getId).collect(toSet()));
        observers.forEach(observer -> observer.deliverFinalizedBlocks(finalized, discarded));
    }

}
