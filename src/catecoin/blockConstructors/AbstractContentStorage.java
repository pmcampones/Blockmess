package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toMap;
import static sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoET.INITIALIZATION_TIME;

public abstract class AbstractContentStorage<E extends IndexableContent>
        implements PrototypicalContentStorage<E> {

    private static final Logger logger = LogManager.getLogger(AbstractContentStorage.class.getName());

    public static final int MAX_THRESHOLD_THROUGHPUT = Integer.MAX_VALUE;

    public static final int MAX_BLOCK_SIZE = 40000;

    public static long timeStart = -1;

    private final int maxBlockSize;

    private final int maxSizeOffset;

    /**
     * Maximum throughput allowed in number of txs per second.
     * Used to throttle load in the application for the experimental evaluation.
     */
    private final int maxThresholdThroughput;

    /**
     * Factor by which the load is lowered in this particular chain.
     * Used to emulate the load a chain would have based on the number of Chaining that has ocurred.
     */
    private int loadBalancing = 1;

    private final Map<UUID, E> contentMap;

    private final MempoolManager<E,?> mempoolManager;

    private final boolean useRandomTransactionAllocation;

    public AbstractContentStorage(Properties props, MempoolManager<E,?> mempoolManager) {
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",
                String.valueOf(MAX_BLOCK_SIZE)));
        this.maxSizeOffset = 1000;
        this.maxThresholdThroughput = parseInt(props.getProperty(("maxThresholdThroughput"),
                String.valueOf(MAX_THRESHOLD_THROUGHPUT)));
        this.useRandomTransactionAllocation = props.getProperty("useRandomTransactionAllocation", "F")
                .equalsIgnoreCase("T");
        if (useRandomTransactionAllocation)
            contentMap = Collections.synchronizedMap(new TreeMap<>());
        else
            contentMap = new ConcurrentHashMap<>();
        int initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
        if (timeStart == -1)
            timeStart = System.currentTimeMillis() + initializationTime;
        this.mempoolManager = mempoolManager;
    }

    @Override
    public abstract List<E> generateBlockContentList(Collection<UUID> states, int usedSpace) throws IOException;

    @Override
    public abstract List<E> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs) throws IOException;

    protected List<E> getContentList(int usedSpace, Set<UUID> used) throws IOException {
        return this.useRandomTransactionAllocation
                ? getContentRandomOrder(usedSpace, used)
                : getContentDeterministicOrder(usedSpace, used);
    }

    protected List<E> getContentWithinBounds(int usedSpace, Set<UUID> used, int maxTxs) throws IOException {
        return getContentDeterministicOrderBound(usedSpace, used, maxTxs);
    }

    private List<E> getContentDeterministicOrder(int usedSpace, Set<UUID> used) throws IOException {

        long elapsedTime = Math.max(0, (System.currentTimeMillis() - timeStart) / 1000);
        int maxNumTxs = (int) Math.min(Integer.MAX_VALUE,
                (((double) elapsedTime) * this.maxThresholdThroughput - mempoolManager.computeNumOfUsedTransactions()) / loadBalancing);
        return getContentDeterministicOrderBound(usedSpace, used, maxNumTxs);
    }

    @NotNull
    private List<E> getContentDeterministicOrderBound(int usedSpace, Set<UUID> used, int maxNumTxs) throws IOException {
        logger.debug(loadBalancing + " : " + maxNumTxs * loadBalancing + " : " + maxNumTxs);
        Iterator<Map.Entry<UUID, E>> contentEntries = contentMap.entrySet().iterator();
        List<E> content = new ArrayList<>();
        while (contentEntries.hasNext() && content.size() < maxNumTxs
                && usedSpace < maxBlockSize - maxSizeOffset) {
            Map.Entry<UUID, E> contentEntry = contentEntries.next();
            if (!used.contains(contentEntry.getKey())) {
                content.add(contentEntry.getValue());
                usedSpace += contentEntry.getValue().getSerializedSize();
            }
        }
        return content;
    }

    private List<E> getContentRandomOrder(int usedSpace, Set<UUID> used) throws IOException {
        long elapsedTime = Math.max(0, (System.currentTimeMillis() - timeStart) / 1000);
        int txsMaxIndex = (int) Math.min(elapsedTime * this.maxThresholdThroughput, contentMap.size());
        return getContentRandomOrderBound(usedSpace, used, (double) elapsedTime, txsMaxIndex);
    }

    @NotNull
    private List<E> getContentRandomOrderBound(int usedSpace, Set<UUID> used, double elapsedTime, int txsMaxIndex) throws IOException {
        List<E> allowedContentList = sublist(contentMap.values(), 0, txsMaxIndex)
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

    private List<E> extractRandomIndexes(List<E> allowedContentList, int maxNumTxs, int usedSpace)
            throws IOException {
        LinkedList<E> allowedLList = new LinkedList<>(allowedContentList);
        Random r = new Random();
        List<E> toReturn = new ArrayList<>(maxNumTxs);
        while (toReturn.size() < maxNumTxs && usedSpace < maxBlockSize - maxSizeOffset) {
            E contentPiece = allowedLList.remove(r.nextInt(allowedLList.size()));
            usedSpace += contentPiece.getSerializedSize();
            toReturn.add(contentPiece);
        }
        return toReturn;
    }

    private List<E> sublist(Collection<E> values, int start, int end) {
        List<E> sublist = new ArrayList<>(end - start);
        Iterator<E> ogIt = List.copyOf(values).listIterator(start);
        for (int i = start; i < end && ogIt.hasNext(); i++)
            sublist.add(ogIt.next());
        return sublist;
    }

    @Override
    public void submitContent(Collection<E> content) {
        contentMap.putAll(content.stream()
                .collect(toMap(IndexableContent::getId,c->c)));
    }

    @Override
    public void submitContent(E content) {
        contentMap.put(content.getId(), content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentIds.forEach(contentMap::remove);
    }

    @Override
    public Collection<E> getStoredContent() {
        return contentMap.values();
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
