package broadcastProtocols;

import broadcastProtocols.lazyPush.timers.PruneTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * An hash map that periodically deletes old entries.
 * Items can be deleted if they have belonged to the dictionary for longer than the product of the instance's parameters,
 * in milliseconds.
 */
public class PeriodicPrunableHashMap<K, V> extends GenericProtocol implements Map<K, V>{

    private static final Logger logger = LogManager.getLogger(PeriodicPrunableHashMap.class);

    private static int instance = 0;

    /**
     * Default period of time between pruning messages.
     */
    public static final long MESSAGE_PRUNE_PERIOD = 30 * 1000; //Milliseconds

    /**
     * Default expected amount of messages per pruning period
     */
    public static final int PERIOD_BUFFER_CAPACITY = 100;

    private static final short NUM_BUCKETS = 3;

    private static final int DEFAULT_CAPACITY = 10;

    private final ConcurrentHashMap<K,V>[] buckets;

    private int currentBucket = 0;

    /**
     * Creates a new map that deletes items in the given period.
     * The time <code>t</code> an item stays in the collection is between: period * (numBuckets - 1)  <= t <= period * numBuckets.
     * @param period Period of time between pruning operations. The shorter the period, the more often will old content be removed.
     */
    public PeriodicPrunableHashMap(long period) throws HandlerRegistrationException {
        this(period, NUM_BUCKETS, DEFAULT_CAPACITY);
    }

    /**
     * Creates a new map that deletes items in the given period.
     * The time <code>t</code> an item stays in the collection is between: period * (numBuckets - 1)  <= t <= period * numBuckets.
     * @param period Period of time between pruning operations. The shorter the period, the more often will old content be removed.
     * @param capacity Initial capacity for each bucket in the data structure.
     */
    public PeriodicPrunableHashMap(long period, int capacity) throws HandlerRegistrationException {
        this(period, NUM_BUCKETS, capacity);
    }

    /**
     * Creates a new map that deletes items in the given period.
     * The time <code>t</code> an item stays in the collection is between: period * (numBuckets - 1)  <= t <= period * numBuckets.
     * @param period Period of time between pruning operations. The shorter the period, the more often will old content be removed.
     * @param numBuckets Number of buckets in the data structure. The more buckets exist, the less items are deleted per pruning operation.
     * @param capacity Initial capacity for each bucket in the data structure.
     */
    public PeriodicPrunableHashMap(long period, short numBuckets, int capacity) throws HandlerRegistrationException {
        super(PeriodicPrunableHashMap.class.getSimpleName() + instance, IDGenerator.genId());
        instance++;
        buckets = new ConcurrentHashMap[numBuckets];
        for (int i = 0; i < numBuckets; i++)
            buckets[i] = new ConcurrentHashMap<>(capacity);
        registerTimerHandler(PruneTimer.ID, (PruneTimer t, long tId) -> uponTimer());
        setupPeriodicTimer(new PruneTimer(), period, period);
    }

    @Override
    public void init(Properties properties) {}

    /**
     * Deletes the items in the oldest bucket and advances the index of the current bucket items are added
     */
    private void uponTimer() {
        buckets[(currentBucket + buckets.length - 1) % buckets.length].clear();
        currentBucket = (currentBucket + 1) % buckets.length;
        logger.debug("CurrentElems: {}", Arrays.stream(buckets).mapToInt(Map::size).sum());
    }

    @Override
    public int size() {
        return Arrays.stream(buckets).mapToInt(Map::size).sum();
    }

    @Override
    public boolean isEmpty() {
        return Arrays.stream(buckets).allMatch(Map::isEmpty);
    }

    @Override
    public boolean containsKey(Object key) {
        return Arrays.stream(buckets).anyMatch(b -> b.containsKey(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return Arrays.stream(buckets).anyMatch(b -> b.containsValue(value));
    }

    @Override
    public V get(Object key) {
        for (Map<K, V> bucket : buckets) {
            V val = bucket.get(key);
            if (val != null) return val;
        }
        return null;
    }

    /**
     * Places an item in the data structure, as in a traditional map implementation.
     * In particular, the new item will be placed in the current bucket.
     */
    @Override
    public V put(K key, V value) {
        V old = remove(key);
        buckets[currentBucket].put(key, value);
        return old;
    }

    @Override
    public V remove(Object key) {
        for (Map<K, V> bucket : buckets) {
            V val = bucket.remove(key);
            if (val != null) return val;
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach((k,v) -> remove(k));
        buckets[currentBucket].putAll(m);
    }

    @Override
    public void clear() {
        Arrays.stream(buckets).forEach(Map::clear);
    }

    @Override
    public Set<K> keySet() {
        return Arrays.stream(buckets)
                .flatMap(b -> b.keySet().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return Arrays.stream(buckets)
                .flatMap(b -> b.values().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return Arrays.stream(buckets)
                .flatMap(b -> b.entrySet().stream())
                .collect(Collectors.toSet());
    }
}
