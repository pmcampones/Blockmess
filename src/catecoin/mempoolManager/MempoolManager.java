package catecoin.mempoolManager;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.txs.IndexableContent;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.SybilElectionProof;
import utils.IDGenerator;

import java.io.IOException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MempoolManager<E extends IndexableContent, P extends SybilElectionProof> extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(MempoolManager.class);

    public static final short ID = IDGenerator.genId();

    public static final int NUMBER_COINS = 10000;

    private final ReadWriteLock mempoolLock = new ReentrantReadWriteLock();

    /**The complete collection of unused UTXOs in the finalized portion of the system**/
    public final Map<UUID, MempoolChunk> mempool = new ConcurrentHashMap<>();

    private final ReadWriteLock utxosLock = new ReentrantReadWriteLock();

    /**UTXOs and tx that have been used/issued in blocks that are yet to be finalized.**/
    //public final Map<UUID, StorageUTXO> utxos = new ConcurrentHashMap<>();

    public int usedTxs = 0;

    /**
     * Contains the file the relative path to the file where the contents will be placed.
     * <p>This field is empty if this node is not recording the blocks.</p>
     */
    private final RecordModule recordModule;

    private final MempoolChunkCreator<E,P> mempoolChunkCreator;

    public MempoolManager(Properties props, MempoolChunkCreator<E,P> mempoolChunkCreator,
                          RecordModule recordModule, BootstrapModule bootstrapModule) throws Exception {
        super(MempoolManager.class.getSimpleName(), ID);
        this.mempoolChunkCreator = mempoolChunkCreator;
        this.recordModule = recordModule;
        loadInitialUtxos(props);
        bootstrapDL(props);
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID, (DeliverNonFinalizedBlockNotification<LedgerBlock<BlockContent<E>, P>> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID, (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification(notif));
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
    }

    private void loadInitialUtxos(Properties props) throws Exception {
        PublicKey originalPublic = CryptographicUtils.readECDSAPublicKey(props.getProperty("originalPublic"));
        int numberCoins = parseInt(props.getProperty("numberCoins", String.valueOf(NUMBER_COINS)));
        List<StorageUTXO> utxos = IntStream.range(0, numberCoins)
                .mapToObj(i -> new StorageUTXO(new UUID(0, i), 1, originalPublic))
                .collect(toList());
        UTXOCollection.updateUtxos(utxos, Collections.emptyList());
    }

    private void bootstrapDL(Properties props) {
        logger.info("Bootstrapping DL");
        List<MempoolChunk> chunks = BootstrapModule.getStoredChunks(props);
        List<StorageUTXO> addedUtxos = chunks.stream()
                .flatMap(chunk -> chunk.getAddedUtxos().stream()).collect(toList());
        List<UUID> removedUtxos = chunks.stream()
                .flatMap(chunk -> chunk.getRemovedUtxos().stream()).collect(toList());
        UTXOCollection.updateUtxos(addedUtxos, removedUtxos);
        logger.info("Successfully bootstrapped {} blocks.", chunks.size());
    }



    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<LedgerBlock<BlockContent<E>, P>> notif) {
        LedgerBlock<BlockContent<E>, P> block = notif.getNonFinalizedBlock();
        logger.debug("Received non finalized block with id {}", block.getBlockId());
        MempoolChunk chunk = mempoolChunkCreator.createChunk(block, notif.getCumulativeWeight());
        mempool.put(chunk.getId(), chunk);
    }

    private void uponDeliverFinalizedBlockNotification(DeliverFinalizedBlockIdentifiersNotification notif) {
        notif.getDiscardedBlockIds().forEach(mempool::remove);
        finalize(notif.getFinalizedBlocksIds());
    }

    public void finalize(List<UUID> finalized) {
        List<MempoolChunk> finalizedChunks = finalized.stream().map(mempool::get).collect(toList());
        recordBlocks(finalizedChunks);
        finalizeBlocks(finalizedChunks);
        usedTxs += finalizedChunks.stream()
                .map(MempoolChunk::getUsedTxs)
                .mapToInt(Set::size).sum();
    }

    private void recordBlocks(List<MempoolChunk> finalized) {
        try {
            recordModule.recordBlocks(finalized);
        } catch (IOException | UnexpectedChunkTypeException e) {
            logger.error("Unable to write to file because: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void finalizeBlocks(List<MempoolChunk> finalized) {
        try {
            mempoolLock.writeLock().lock();
            utxosLock.writeLock().lock();
            tryToFinalizeBlocks(finalized);
        } finally {
            mempoolLock.writeLock().unlock();
            utxosLock.writeLock().unlock();
        }
    }

    private void tryToFinalizeBlocks(List<MempoolChunk> finalized) {
        Set<UUID> removedUtxoIds = extractRemovedUtxosFromBlock(finalized);
        Set<StorageUTXO> addedUtxos = extractStorageUtxosFromBlock(finalized);
        Map<UUID, StorageUTXO> mapAddedUtxos = new HashMap<>();
        Set<StorageUTXO> removedUtxos = getRemovedUtxos(removedUtxoIds, mapAddedUtxos);
        extractUsedTxsFromBlock(finalized);
        triggerNotification(new DeliverFinalizedBlocksContentNotification(
                removedUtxos, addedUtxos));
        UTXOCollection.updateUtxos(addedUtxos, removedUtxoIds);
        finalized.stream().map(MempoolChunk::getId).forEach(mempool::remove);
    }

    private Set<UUID> extractRemovedUtxosFromBlock(List<MempoolChunk> blocks) {
        return blocks.stream()
                .flatMap(b -> b.getRemovedUtxos().stream())
                .collect(toSet());
    }

    private Set<StorageUTXO> extractStorageUtxosFromBlock(List<MempoolChunk> blocks) {
        return blocks.stream()
                .peek(b -> logger.info("Finalized block {}, with {} UTXOs and {} txs.",
                        b.getId(), b.getAddedUtxos().size(), b.getUsedTxs().size()))
                .flatMap(b -> b.getAddedUtxos().stream())
                .collect(toSet());
    }

    private Set<UUID> extractUsedTxsFromBlock(List<MempoolChunk> blocks) {
        return blocks.stream()
                .flatMap(b -> b.getUsedTxs().stream())
                .collect(toSet());
    }

    private Set<StorageUTXO> getRemovedUtxos(Set<UUID> removedIds, Map<UUID, StorageUTXO> addedUtxos) {
        Set<StorageUTXO> removedUtxos = new HashSet<>(removedIds.size());
        Collection<Optional<StorageUTXO>> storedUtxos = UTXOCollection.getUtxos(removedIds);
        /*for (UUID id : removedIds) {
            StorageUTXO rem = utxos.get(id);
            if (rem == null)
                rem = addedUtxos.get(id);
            removedUtxos.add(rem);
        }*/
        Iterator<Optional<StorageUTXO>> storedUtxosIt = storedUtxos.iterator();
        Iterator<UUID> removedIdsIt = removedIds.iterator();
        while (storedUtxosIt.hasNext()) {
            Optional<StorageUTXO> utxo = storedUtxosIt.next();
            UUID id = removedIdsIt.next();
            StorageUTXO toRem = utxo.orElseGet(() -> addedUtxos.get(id));
            removedUtxos.add(toRem);
        }
        return removedUtxos;
    }

    public Set<UUID> getInvalidTxsFromChunk(UUID previousState, Set<UUID> visited) {
        MempoolChunk chunk = mempool.get(previousState);
        if (visited.contains(previousState) || chunk == null) return Collections.emptySet();
        visited.add(previousState);
        Set<UUID> invalid = new HashSet<>(chunk.getUsedTxs());
        chunk.getPreviousChunksIds().forEach(id -> invalid.addAll(getInvalidTxsFromChunk(id, visited)));
        return invalid;
    }

    public Set<StorageUTXO> getValidUtxosFromChunk(UUID chunkId) {
        Set<UUID> invalid = getInvalidUtxosFromChunk(chunkId, new HashSet<>());
        Set<StorageUTXO> added = getAddedUtxosFromChunk(chunkId, new HashSet<>());
        return added.stream()
                .filter(s -> !invalid.contains(s.getId()))
                .collect(toSet());
    }

    public Set<UUID> getInvalidUtxosFromChunk(UUID chunkId, Set<UUID> visited) {
        MempoolChunk chunk = mempool.get(chunkId);
        if (visited.contains(chunkId) || chunk == null) return Collections.emptySet();
        visited.add(chunkId);
        Set<UUID> invalid = new HashSet<>(chunk.getRemovedUtxos());
        chunk.getPreviousChunksIds().forEach(id -> invalid.addAll(getInvalidUtxosFromChunk(id, visited)));
        return invalid;
    }

    public Set<StorageUTXO> getAddedUtxosFromChunk(UUID chunkId, Set<UUID> visited) {
        MempoolChunk chunk = mempool.get(chunkId);
        if (visited.contains(chunkId) || chunk == null) return Collections.emptySet();
        visited.add(chunkId);
        Set<StorageUTXO> valid = new HashSet<>(chunk.getAddedUtxos());
        chunk.getPreviousChunksIds()
                .forEach(id -> valid.addAll(getAddedUtxosFromChunk(id, visited)));
        return valid;
    }

    /*public StorageUTXO getUTXOContent(UUID utxoId) {
        return utxos.get(utxoId);
    }*/

    public Lock getMempoolReadLock() {
        return mempoolLock.readLock();
    }

    public int computeNumOfUsedTransactions() {
        return usedTxs + mempool.values()
                .stream()
                .map(MempoolChunk::getUsedTxs)
                .mapToInt(Set::size)
                .sum();
    }

}
