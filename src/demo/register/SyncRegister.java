package demo.register;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigInteger;

public class SyncRegister {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) printUsageMessage();
        else execute(args);
    }

    private static void printUsageMessage() {
        System.out.println("Missing required arguments:");
        System.out.println("Usage: java -cp demo.counter.SyncRegister op_idx register_change num_updates [property=value]*");
        System.out.println("op_idx: Index of the operation to be executed. 0 is a sum, 1 is a multiplication.");
        System.out.println("register_change: Update to the value of the shared counter in each update.");
        System.out.println("num_updates: Number of updates to be executed synchronously.");
        System.out.println("[property=value]*: List of property values to override those in the configuration file.");
    }

    private static void execute(String[] args) throws IOException {
        short opIdx = Short.parseShort(args[0]);
        Register.OP op = Register.OP.values()[opIdx];
        int val = Integer.parseInt(args[1]);
        byte[] operation = Register.serializeOperation(op, val);
        int numUpdates = Integer.parseInt(args[2]);
        String[] blockmessProperties = Register.sliceArray(args, 3, args.length);
        Register registerService = new Register(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateDistributedRegister(registerService, operation, i);
    }

    private static void updateDistributedRegister(Register registerService, byte[] operation, int i) {
        Pair<byte[], Long> res = registerService.invokeSyncOperation(operation);
        byte[] registerBytes = res.getLeft();
        long opIdx = res.getRight();
        BigInteger registerVal = new BigInteger(registerBytes, 0, registerBytes.length);
        System.out.printf("Register with value %s on local update %d and global operation %d%n", registerVal, i, opIdx);
    }

}
