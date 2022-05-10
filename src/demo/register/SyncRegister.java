package demo.register;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigInteger;

public class SyncRegister {

    public static void main(String[] args) throws IOException {
        short opIdx = Short.parseShort(args[0]);
        Register.OP op = Register.OP.values()[opIdx];
        int val = Integer.parseInt(args[1]);
        byte[] operation = Register.serializeOperation(op, val);
        int numUpdates = Integer.parseInt(args[2]);
        String[] blockmessProperties = Register.sliceArray(args, 3, args.length);
        Register registerService = new Register(blockmessProperties);
        for (int i = 0; i < numUpdates; i++)
            updateRegister(registerService, operation, i);
    }

    private static void updateRegister(Register registerService, byte[] operation, int i) {
        Pair<byte[], Long> res = registerService.invokeSyncOperation(operation);
        byte[] registerBytes = res.getLeft();
        long opIdx = res.getRight();
        BigInteger registerVal = new BigInteger(registerBytes, 0, registerBytes.length);
        System.out.printf("Register with value %s on local update %d and global operation %d%n", registerVal, i, opIdx);
    }

}
