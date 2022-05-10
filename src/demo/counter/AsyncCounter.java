package demo.counter;

public class AsyncCounter {

    public static void main(String[] args) {
        int change = Integer.parseInt(args[0]);
        byte[] changeBytes = Counter.numToBytes(change);
        int numUpdates = Integer.parseInt(args[1]);
        String[] blockmessProperties = Counter.sliceArray(args, 2, args.length);
        Counter counterServer = new Counter(blockmessProperties);
        for (int i = 0; i < numUpdates; i++) {
            int finalI = i;
            counterServer.invokeAsyncOperation(changeBytes, operationResult -> {
                byte[] currCounterBytes = operationResult.getLeft();
                long opIdx = operationResult.getRight();
                int currCounter = Counter.bytesToInt(currCounterBytes);
                System.out.printf("Counter with value %d on local update %d and global operation %d%n",
                        currCounter, finalI, opIdx);
            });
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
