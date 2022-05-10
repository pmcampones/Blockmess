package demo.counter;

import applicationInterface.ApplicationInterface;

import java.nio.ByteBuffer;

public class Counter extends ApplicationInterface {

    private static int counter = 0;

    public Counter(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        int change = Integer.parseInt(args[0]);
        byte[] changeBytes = numToBytes(change);
        int numUpdates = Integer.parseInt(args[1]);
        String[] blockmessProperties = sliceArray(args, 2, args.length);
        Counter counterServer = new Counter(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateCounter(counterServer, changeBytes, i);
    }

    private static void updateCounter(Counter counterServer, byte[] changeBytes, int i) {
        byte[] operation = getOperation(changeBytes, i);
        byte[] currCounterBytes = counterServer.invokeOperation(operation);
        int currCounter = bytesToInt(currCounterBytes);
        System.out.printf("Counter with value %d on update %d%n", currCounter, i);
    }

    private static byte[] getOperation(byte[] changeBytes, int i) {
        byte[] operation = new byte[2 * Integer.BYTES];
        byte[] opIndex = numToBytes(i);
        System.arraycopy(changeBytes, 0, operation, 0, changeBytes.length);
        System.arraycopy(opIndex, 0, operation, changeBytes.length, opIndex.length);
        return operation;
    }

    private static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static byte[] numToBytes(int num) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(num).array();
    }

    private static String[] sliceArray(String[] array, int startIdx, int endIdx) {
        String[] slice = new String[endIdx - startIdx];
        System.arraycopy(array, startIdx, slice, 0, endIdx - startIdx);
        return slice;
    }

    @Override
    public byte[] processOperation(byte[] operation) {
        int change = bytesToInt(operation);
        counter += change;
        return numToBytes(counter);
    }
}
