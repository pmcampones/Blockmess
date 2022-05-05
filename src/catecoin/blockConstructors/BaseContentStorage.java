package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import ledger.ledgerManager.StructuredValue;
import main.GlobalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toMap;
import static sybilResistantElection.SybilResistantElection.INITIALIZATION_TIME;

public class BaseContentStorage
        implements PrototypicalContentStorage<StructuredValue<SlimTransaction>> {

    public static final int MAX_THRESHOLD_THROUGHPUT = Integer.MAX_VALUE;
    public static final int MAX_BLOCK_SIZE = 40000;
    private static final Logger logger = LogManager.getLogger(BaseContentStorage.class.getName());
    public static long timeStart = -1;
    private final MempoolManager mempoolManager = MempoolManager.getSingleton();

    private final int maxBlockSize;
    private final int maxSizeOffset;
    /**
     * Maximum throughput allowed in number of txs per second.
     * Used to throttle load in the application for the experimental evaluation.
     */
    private final int maxThresholdThroughput;
    private final Map<UUID, StructuredValue<SlimTransaction>> contentMap;
    private final boolean useRandomTransactionAllocation;
    /**
     * Factor by which the load is lowered in this particular chain.
     * Used to emulate the load a chain would have based on the number of Chaining that has ocurred.
     */
    private int loadBalancing = 1;

    public BaseContentStorage() {
        Properties props = GlobalProperties.getProps();
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",
                String.valueOf(MAX_BLOCK_SIZE)));
        this.maxSizeOffset = 1000;
        this.maxThresholdThroughput = parseInt(props.getProperty(("maxThresholdThroughput"),
                String.valueOf(MAX_THRESHOLD_THROUGHPUT)));
        this.useRandomTransactionAllocation = props.getProperty("useRandomTransactionAllocation", "F")
                .equalsIgnoreCase("T");
        contentMap = useRandomTransactionAllocation ? Collections.synchronizedMap(new TreeMap<>()) : new ConcurrentHashMap<>();
        int initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
        if (timeStart == -1)
            timeStart = System.currentTimeMillis() + initializationTime;
    }

    @Override
    public List<StructuredValue<SlimTransaction>> generateContentListList(Collection<UUID> states, int usedSpace) throws IOException {
        Set<UUID> used = findUsedTransactions(states);
        return getContentList(usedSpace, used);
    }

    @Override
    public List<StructuredValue<SlimTransaction>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs) throws IOException {
        Set<UUID> used = findUsedTransactions(states);
        return getContentWithinBounds(usedSpace, used, maxTxs);
    }

    private Set<UUID> findUsedTransactions(Collection<UUID> states) {
        Set<UUID> used = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID state : states)
            used.addAll(mempoolManager.getInvalidTxsFromChunk(state, visited));
        return used;
    }

    protected List<StructuredValue<SlimTransaction>> getContentWithinBounds(int usedSpace, Set<UUID> used, int maxTxs) throws IOException {
        return getContentDeterministicOrderBound(usedSpace, used, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<SlimTransaction>> content) {
        contentMap.putAll(content.stream()
                .collect(toMap(IndexableContent::getId,c->c)));
    }

    @Override
    public void submitContent(StructuredValue<SlimTransaction> content) {
        contentMap.put(content.getId(), content);
    }

    @Override
    public Collection<StructuredValue<SlimTransaction>> getStoredContent() {
        return contentMap.values();
    }

    protected List<StructuredValue<SlimTransaction>> getContentList(int usedSpace, Set<UUID> used) throws IOException {
        return this.useRandomTransactionAllocation
                ? getContentRandomOrder(usedSpace, used)
                : getContentDeterministicOrder(usedSpace, used);
    }

    private List<StructuredValue<SlimTransaction>> getContentDeterministicOrder(int usedSpace, Set<UUID> used) throws IOException {

        long elapsedTime = Math.max(0, (System.currentTimeMillis() - timeStart) / 1000);
        int maxNumTxs = (int) Math.min(Integer.MAX_VALUE,
                (((double) elapsedTime) * this.maxThresholdThroughput - mempoolManager.computeNumOfUsedTransactions()) / loadBalancing);
        return getContentDeterministicOrderBound(usedSpace, used, maxNumTxs);
    }

    @NotNull
    private List<StructuredValue<SlimTransaction>> getContentDeterministicOrderBound(int usedSpace, Set<UUID> used, int maxNumTxs) throws IOException {
        logger.debug(loadBalancing + " : " + maxNumTxs * loadBalancing + " : " + maxNumTxs);
        Iterator<Map.Entry<UUID, StructuredValue<SlimTransaction>>> contentEntries = contentMap.entrySet().iterator();
        List<StructuredValue<SlimTransaction>> content = new ArrayList<>();
        while (contentEntries.hasNext() && content.size() < maxNumTxs
                && usedSpace < maxBlockSize - maxSizeOffset) {
            Map.Entry<UUID, StructuredValue<SlimTransaction>> contentEntry = contentEntries.next();
            if (!used.contains(contentEntry.getKey())) {
                content.add(contentEntry.getValue());
                usedSpace += contentEntry.getValue().getSerializedSize();
            }
        }
        return content;
    }

    private List<StructuredValue<SlimTransaction>> getContentRandomOrder(int usedSpace, Set<UUID> used) throws IOException {
        long elapsedTime = Math.max(0, (System.currentTimeMillis() - timeStart) / 1000);
        int txsMaxIndex = (int) Math.min(elapsedTime * this.maxThresholdThroughput, contentMap.size());
        return getContentRandomOrderBound(usedSpace, used, (double) elapsedTime, txsMaxIndex);
    }

    @NotNull
    private List<StructuredValue<SlimTransaction>> getContentRandomOrderBound(int usedSpace, Set<UUID> used, double elapsedTime, int txsMaxIndex) throws IOException {
        List<StructuredValue<SlimTransaction>> allowedContentList = sublist(contentMap.values(), 0, txsMaxIndex)
                .stream()
                .filter(content -> !used.contains(content.getId()))
                .collect(Collectors.toList());
        int maxNumTxs = (int) Math.min(Integer.MAX_VALUE,
                (elapsedTime * this.maxThresholdThroughput - mempoolManager.computeNumOfUsedTransactions()) / loadBalancing);
        System.out.println(loadBalancing + " : " + maxNumTxs * loadBalancing + " : " + maxNumTxs);
        return maxNumTxs >= allowedContentList.size()
                ? allowedContentList
                : extractRandomIndexes(allowedContentList, maxNumTxs, usedSpace);
    }

    private List<StructuredValue<SlimTransaction>> extractRandomIndexes(List<StructuredValue<SlimTransaction>> allowedContentList, int maxNumTxs, int usedSpace)
            throws IOException {
        LinkedList<StructuredValue<SlimTransaction>> allowedLList = new LinkedList<>(allowedContentList);
        Random r = new Random();
        List<StructuredValue<SlimTransaction>> toReturn = new ArrayList<>(maxNumTxs);
        while (toReturn.size() < maxNumTxs && usedSpace < maxBlockSize - maxSizeOffset) {
            StructuredValue<SlimTransaction> contentPiece = allowedLList.remove(r.nextInt(allowedLList.size()));
            usedSpace += contentPiece.getSerializedSize();
            toReturn.add(contentPiece);
        }
        return toReturn;
    }

    private List<StructuredValue<SlimTransaction>> sublist(Collection<StructuredValue<SlimTransaction>> values, int start, int end) {
        List<StructuredValue<SlimTransaction>> sublist = new ArrayList<>(end - start);
        Iterator<StructuredValue<SlimTransaction>> ogIt = List.copyOf(values).listIterator(start);
        for (int i = start; i < end && ogIt.hasNext(); i++)
            sublist.add(ogIt.next());
        return sublist;
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentIds.forEach(contentMap::remove);
    }

    @Override
    public PrototypicalContentStorage<StructuredValue<SlimTransaction>> clonePrototype() {
        return new BaseContentStorage();
    }

    @Override
    public void halveChainThroughput() {
        loadBalancing *= 2;
    }

    @Override
    public void doubleChainThroughput() {
        loadBalancing /= 2;
    }

    public int getThroughputReduction(){
        return loadBalancing;
    }

    public void setChainThroughputReduction(int reduction) {
        loadBalancing = reduction;
    }
}
