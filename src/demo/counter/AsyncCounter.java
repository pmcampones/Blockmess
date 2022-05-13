package demo.counter;

public class AsyncCounter {

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) printUsageMessage();
        else execute(args);
    }

    private static void printUsageMessage() {
        System.out.println("Missing required arguments:");
        System.out.println("Usage: java -cp demo.counter.AsyncCounter counter_change num_updates [property=value]*");
        System.out.println("counter_change: Update to the value of the shared counter in each update.");
        System.out.println("num_updates: Number of updates to be executed asynchronously.");
        System.out.println("[property=value]*: List of property values to override those in the configuration file.");
    }

    private static void execute(String[] args) throws InterruptedException {
        int change = Integer.parseInt(args[0]);
        byte[] changeBytes = Counter.numToBytes(change);
        int numUpdates = Integer.parseInt(args[1]);
        String[] blockmessProperties = Counter.sliceArray(args, 2, args.length);
        Counter counterServer = new Counter(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateDistributedCounter(changeBytes, counterServer, i);
    }

    private static void updateDistributedCounter(byte[] changeBytes, Counter counterServer, int i) throws InterruptedException {
        counterServer.invokeAsyncOperation(changeBytes, operationResult -> {
            byte[] currCounterBytes = operationResult.getLeft();
            long opIdx = operationResult.getRight();
            int currCounter = Counter.bytesToInt(currCounterBytes);
            System.out.printf("Counter with value %d on local update %d and global operation %d%n", currCounter, i, opIdx);
        });
        //Sleeping between operations increases the interleaving between replicas, but it is not necessary
        Thread.sleep(10);
    }

}
