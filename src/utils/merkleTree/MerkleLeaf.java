package utils.merkleTree;

import java.util.Arrays;
import java.util.Set;

public class MerkleLeaf implements MerkleNode {

    private byte[] hashVal;

    private ParentMerkleNode parent;

    public MerkleLeaf(byte[] hashVal, ParentMerkleNode parent) {
        this.hashVal = hashVal;
        this.parent = parent;
        parent.notifyLeafRef(this);
    }

    public MerkleLeaf(byte[] hashVal) {
        this.hashVal = hashVal;
    }

    @Override
    public byte[] getHashValue() {
        return hashVal;
    }

    @Override
    public void addLeaf(byte[] hashVal) {
        MerkleLeaf rgt = new MerkleLeaf(hashVal);
        InnerNode innerNode = new InnerNode(parent, this, rgt);
        rgt.setParent(innerNode);
        parent.replaceChild(this, innerNode);
        this.parent = innerNode;
        parent.notifyLeafRef(rgt);
    }

    @Override
    public boolean removeLeaf(byte[] hashVal) {
        if (Arrays.equals(hashVal, this.hashVal)) {
            parent.removeChild(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean replaceLeaf(byte[] oldVal, byte[] newVal) {
        if (Arrays.equals(oldVal, hashVal)) {
            hashVal = newVal;
            parent.percolateChange();
            return true;
        } return false;
    }

    @Override
    public Set<byte[]> getLeaves() {
        return Set.of(hashVal);
    }

    @Override
    public void setParent(ParentMerkleNode parent) {
        this.parent = parent;
    }
}
