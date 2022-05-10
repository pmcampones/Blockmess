package demo.counter;

import org.apache.commons.lang3.tuple.Pair;

public class SyncCounter {

    public static void main(String[] args) {
        int change = Integer.parseInt(args[0]);
        byte[] changeBytes = Counter.numToBytes(change);
        int numUpdates = Integer.parseInt(args[1]);
        String[] blockmessProperties = Counter.sliceArray(args, 2, args.length);
        Counter counterServer = new Counter(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateCounter(counterServer, changeBytes, i);
    }

    private static void updateCounter(Counter counterServer, byte[] operation, int i) {
        Pair<byte[], Long> res = counterServer.invokeSyncOperation(operation);
        byte[] currCounterBytes = res.getLeft();
        long opIdx = res.getRight();
        int currCounter = Counter.bytesToInt(currCounterBytes);
        System.out.printf("Counter with value %d on local update %d and global operation %d%n", currCounter, i, opIdx);
    }

}
