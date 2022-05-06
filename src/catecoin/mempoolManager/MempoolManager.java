package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.txs.Transaction;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.LedgerBlock;
import ledger.ledgerManager.StructuredValue;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.GlobalProperties;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;
import utils.IDGenerator;

import java.io.IOException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.*;

public class MempoolManager extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(MempoolManager.class);

    public static final short ID = IDGenerator.genId();

    public static final int NUMBER_COINS = 10000;

    private final ReadWriteLock mempoolLock = new ReentrantReadWriteLock();

    /**The complete collection of unused UTXOs in the finalized portion of the system**/
    public final Map<UUID, MempoolChunk> mempool = new ConcurrentHashMap<>();

    private final ReadWriteLock utxosLock = new ReentrantReadWriteLock();

    public int usedTxs = 0;

    /**
     * Contains the file the relative path to the file where the contents will be placed.
     * <p>This field is empty if this node is not recording the blocks.</p>
     */
    private final MinimalistRecordModule recordModule;

    private static MempoolManager singleton;

    private MempoolManager() throws Exception {
        super(MempoolManager.class.getSimpleName(), ID);
        this.recordModule = new MinimalistRecordModule();
        loadInitialUtxos();
        bootstrapDL();
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof>> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification(notif));
        ProtoPojo.pojoSerializers.put(ContentList.ID, ContentList.serializer);
    }

    private void loadInitialUtxos() throws Exception {
        Properties props = GlobalProperties.getProps();
        PublicKey originalPublic = CryptographicUtils.readECDSAPublicKey(props.getProperty("originalPublic"));
        int numberCoins = parseInt(props.getProperty("numberCoins", String.valueOf(NUMBER_COINS)));
        List<StorageUTXO> utxos = IntStream.range(0, numberCoins)
                .mapToObj(i -> new StorageUTXO(new UUID(0, i), 1, originalPublic))
                .collect(toList());
        UTXOCollection.updateUtxos(utxos, Collections.emptyList());
    }

    private void bootstrapDL() {
        logger.info("Bootstrapping DL");
        List<MempoolChunk> chunks = BootstrapModule.getStoredChunks();
        List<StorageUTXO> addedUtxos = chunks.stream()
                .flatMap(chunk -> chunk.getAddedUtxos().stream()).collect(toList());
        List<UUID> removedUtxos = chunks.stream()
                .flatMap(chunk -> chunk.getRemovedUtxos().stream()).collect(toList());
        UTXOCollection.updateUtxos(addedUtxos, removedUtxos);
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
            DeliverNonFinalizedBlockNotification<LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof>> notif) {
        LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> block = notif.getNonFinalizedBlock();
        logger.debug("Received non finalized block with id {}", block.getBlockId());
        MempoolChunk chunk = createChunk(block, notif.getCumulativeWeight());
        mempool.put(chunk.getId(), chunk);
    }

    private MempoolChunk createChunk(LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> block, int cumulativeWeight) {
        List<Transaction> unwrappedContent = block.getContentList()
                .getContentList()
                .stream()
                .map(StructuredValue::getInnerValue)
                .collect(toList());
        Set<StorageUTXO> addedUtxos = extractAddedUtxos(unwrappedContent);
        Set<UUID> usedUtxos = extractRemovedUtxos(unwrappedContent);
        Set<UUID> usedTxs = extractUsedTxs(unwrappedContent);
        return new MempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()),
                unmodifiableSet(addedUtxos), usedUtxos, usedTxs, cumulativeWeight);
    }

    private Set<StorageUTXO> extractAddedUtxos(List<Transaction> ContentList) {
        return ContentList.parallelStream().flatMap(tx -> Stream.concat(
                        tx.getOutputsDestination().stream()
                                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getDestination())),
                        tx.getOutputsOrigin().stream()
                                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getOrigin()))
                )
        ).collect(toUnmodifiableSet());
    }

    private Set<UUID> extractRemovedUtxos(List<Transaction> ContentList) {
        return ContentList.stream()
                .flatMap(tx -> tx.getInputs().stream())
                .collect(toUnmodifiableSet());
    }

    private Set<UUID> extractUsedTxs(List<Transaction> ContentList) {
        return ContentList.stream()
                .map(Transaction::getId)
                .collect(toUnmodifiableSet());
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
        } catch (IOException e) {
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
        triggerNotification(new DeliverFinalizedBlocksContentNotification());
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

    private Set<StorageUTXO> getRemovedUtxos(Set<UUID> removedIds, Map<UUID, StorageUTXO> addedUtxos) {
        Set<StorageUTXO> removedUtxos = new HashSet<>(removedIds.size());
        Collection<Optional<StorageUTXO>> storedUtxos = UTXOCollection.getUtxos(removedIds);
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
