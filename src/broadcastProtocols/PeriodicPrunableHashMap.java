package broadcastProtocols;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * An hash map that periodically deletes old entries.
 * Items can be deleted if they have belonged to the dictionary for longer than the product of the instance's parameters,
 * in milliseconds.
 */
public class PeriodicPrunableHashMap<K,V> implements Map<K,V>{

    private static final Logger logger = LogManager.getLogger(PeriodicPrunableHashMap.class);

    private static final ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);;

    /**
     * Default period of time between pruning messages.
     */
    public static final long MESSAGE_PRUNE_PERIOD = 30 * 1000; //Milliseconds

    /**
     * Default expected amount of messages per pruning period
     */
    public static final int PERIOD_BUFFER_CAPACITY = 100;

    private static final short NUM_BUCKETS = 3;

    private final ConcurrentHashMap<K,V>[] buckets;

    private int currentBucket = 0;

    /**
     * Creates a new map that deletes items in the given period.
     * The time <code>t</code> an item stays in the collection is between: period * (numBuckets - 1)  <= t <= period * numBuckets.
     */
    public PeriodicPrunableHashMap() {
        buckets = new ConcurrentHashMap[NUM_BUCKETS];
        for (int i = 0; i < NUM_BUCKETS; i++)
            buckets[i] = new ConcurrentHashMap<>(PERIOD_BUFFER_CAPACITY);
        pool.scheduleAtFixedRate(this::uponTimer, MESSAGE_PRUNE_PERIOD, MESSAGE_PRUNE_PERIOD, TimeUnit.MILLISECONDS);
    }

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
