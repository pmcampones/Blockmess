package utils.merkleTree;

import java.util.Set;

public interface MerkleTree {

    byte[] getHashValue();

    void addLeaf(byte[] hashVal);

    /**
     * @param hashVal The hashValue of the leaf we intend to replace.
     * @return Whether a leaf node with the given hash value was removed.
     */
    boolean removeLeaf(byte[] hashVal);

    /**
     * @param oldVal Old hash value on the leaf node
     * @param newVal The replacement hash value on the leaf node.
     * @return True if the operation was successful and thus a leaf was modified, and false if there was no leaf with
     * the oldVal.
     */
    boolean replaceLeaf(byte[] oldVal, byte[] newVal);

    Set<byte[]> getLeaves();

}
