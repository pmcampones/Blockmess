package demo.register;

import java.io.IOException;
import java.math.BigInteger;

public class AsyncRegister {

    public static void main(String[] args) throws IOException, InterruptedException {
        short opIdx = Short.parseShort(args[0]);
        Register.OP op = Register.OP.values()[opIdx];
        int val = Integer.parseInt(args[1]);
        byte[] operation = Register.serializeOperation(op, val);
        int numUpdates = Integer.parseInt(args[2]);
        String[] blockmessProperties = Register.sliceArray(args, 3, args.length);
        Register registerService = new Register(blockmessProperties);
        for (int i = 0; i < numUpdates; i++) {
            int finalI = i;
            registerService.invokeAsyncOperation(operation, operationResult -> {
                byte[] registerBytes = operationResult.getLeft();
                long opIdx1 = operationResult.getRight();
                BigInteger registerVal = new BigInteger(registerBytes, 0, registerBytes.length);
                System.out.printf("Register with value %s on local update %d and global operation %d%n",
                        registerVal, finalI, opIdx1);
            });
            Thread.sleep(10);
        }
    }

}
