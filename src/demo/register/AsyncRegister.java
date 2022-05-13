package demo.register;

import java.io.IOException;
import java.math.BigInteger;

public class AsyncRegister {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) printUsageMessage();
        else execute(args);
    }

    private static void printUsageMessage() {
        System.out.println("Missing required arguments:");
        System.out.println("Usage: java -cp demo.counter.AsyncRegister op_idx register_change num_updates [property=value]*");
        System.out.println("op_idx: Index of the operation to be executed. 0 is a sum, 1 is a multiplication.");
        System.out.println("register_change: Update to the value of the shared counter in each update.");
        System.out.println("num_updates: Number of updates to be executed asynchronously.");
        System.out.println("[property=value]*: List of property values to override those in the configuration file.");
    }

    private static void execute(String[] args) throws IOException, InterruptedException {
        short opIdx = Short.parseShort(args[0]);
        Register.OP op = Register.OP.values()[opIdx];
        int val = Integer.parseInt(args[1]);
        byte[] operation = Register.serializeOperation(op, val);
        int numUpdates = Integer.parseInt(args[2]);
        String[] blockmessProperties = Register.sliceArray(args, 3, args.length);
        Register registerService = new Register(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateDistributedRegister(operation, registerService, i);
    }

    private static void updateDistributedRegister(byte[] operation, Register registerService, int i) throws InterruptedException {
        registerService.invokeAsyncOperation(operation, operationResult -> {
            byte[] registerBytes = operationResult.getLeft();
            long opIdx1 = operationResult.getRight();
            BigInteger registerVal = new BigInteger(registerBytes, 0, registerBytes.length);
            System.out.printf("Register with value %s on local update %d and global operation %d%n", registerVal, i, opIdx1);
        });
        //Sleeping between operations increases the interleaving between replicas, but it is not necessary
        Thread.sleep(10);
    }

}
