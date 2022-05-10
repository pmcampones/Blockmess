package demo.register;

import applicationInterface.ApplicationInterface;

import java.io.*;
import java.math.BigInteger;

public class Register extends ApplicationInterface {

    BigInteger register = BigInteger.ZERO;

    public Register(String[] blockmessProperties) {
        super(blockmessProperties);
    }

    static String[] sliceArray(String[] array, int startIdx, int endIdx) {
        String[] slice = new String[endIdx - startIdx];
        System.arraycopy(array, startIdx, slice, 0, endIdx - startIdx);
        return slice;
    }

    static byte[] serializeOperation(OP opCode, int val) throws IOException {
        try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
            oout.writeShort(opCode.ordinal());
            oout.writeInt(val);
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }

    @Override
    public byte[] processOperation(byte[] operation) {
        try (var in = new ByteArrayInputStream(operation); var oin = new ObjectInputStream(in)) {
            short opIdx = oin.readShort();
            BigInteger val = BigInteger.valueOf(oin.readInt());
            OP opCode = OP.values()[opIdx];
            switch (opCode) {
                case SUM:
                    register = register.add(val);
                case MULTI:
                    register = register.multiply(val);
            }
            return register.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    enum OP {
        SUM,
        MULTI
    }
}
