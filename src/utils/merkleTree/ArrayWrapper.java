package utils.merkleTree;

import java.util.Arrays;

public class ArrayWrapper {

    private final byte[] array;

    public ArrayWrapper(byte[] array) {
        this.array = array;
    }

    public byte[] getArray() {
        return array;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArrayWrapper)) return false;
        return Arrays.equals(array, ((ArrayWrapper)o).array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }
}
