package utils.merkleTree;

import org.apache.commons.collections4.SetUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static main.CryptographicUtils.HASH_ALGORITHM;

public class InnerNode implements MerkleNode, ParentMerkleNode {

    private ParentMerkleNode parent;

    private MerkleNode lft, rgt;

    private boolean isModified = true;

    private byte[] hashVal = new byte[0];

    private boolean nextLeafLeft = true;

    public InnerNode(ParentMerkleNode parent, MerkleNode lft, MerkleNode rgt) {
        this.parent = parent;
        this.lft = lft;
        this.rgt = rgt;
    }

    public InnerNode(List<byte[]> hashVals, ParentMerkleNode parent) {
        this.parent = parent;
        List<byte[]> lftBytes = new ArrayList<>(hashVals.subList(0, hashVals.size() / 2));
        this.lft = getNode(lftBytes);
        List<byte[]> rgtBytes = new ArrayList<>(hashVals.subList(hashVals.size() / 2, hashVals.size()));
        this.rgt = getNode(rgtBytes);
    }

    private MerkleNode getNode(List<byte[]> bytes) {
        return bytes.size() == 1 ?
                new MerkleLeaf(bytes.get(0), this) :
                new InnerNode(bytes, this);
    }

    @Override
    public byte[] getHashValue() {
        hashVal = isModified ? computeNodeHash() : hashVal;
        isModified = false;
        return hashVal;
    }

    private byte[] computeNodeHash() {
        try {
            return tryToComputeNodeHash();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private byte[] tryToComputeNodeHash() throws NoSuchAlgorithmException {
        byte[] lftBytes = lft.getHashValue();
        byte[] rgtBytes = rgt.getHashValue();
        byte[] fullBytes = new byte[lftBytes.length + rgtBytes.length];
        System.arraycopy(lftBytes, 0, fullBytes, 0, lftBytes.length);
        System.arraycopy(rgtBytes, 0, fullBytes, lftBytes.length, rgtBytes.length);
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(fullBytes);
    }

    @Override
    public void addLeaf(byte[] hashVal) {
        MerkleTree choosen = nextLeafLeft ? lft : rgt;
        nextLeafLeft = !nextLeafLeft;
        choosen.addLeaf(hashVal);
        this.isModified = true;
    }

    @Override
    public boolean removeLeaf(byte[] hashVal) {
        boolean removed = lft.removeLeaf(hashVal) || rgt.removeLeaf(hashVal);
        if (removed)
            this.isModified = true;
        return removed;
    }

    @Override
    public boolean replaceLeaf(byte[] oldVal, byte[] newVal) {
        boolean replaced = lft.replaceLeaf(oldVal, newVal) || rgt.replaceLeaf(oldVal, newVal);
        if (replaced)
            this.isModified = true;
        return replaced;
    }

    @Override
    public Set<byte[]> getLeaves() {
        return SetUtils.union(lft.getLeaves(), rgt.getLeaves());
    }

    @Override
    public void percolateChange() {
        this.isModified = true;
        parent.percolateChange();
    }

    @Override
    public void notifyLeafRef(MerkleLeaf leaf) {
        this.isModified = true;
        parent.notifyLeafRef(leaf);
    }

    @Override
    public void replaceChild(MerkleNode oldChild, MerkleNode newChild) {
        if (lft == oldChild) {
            lft = newChild;
            percolateChange();
        }
        else if (rgt == oldChild) {
            rgt = newChild;
            percolateChange();
        }
        newChild.setParent(this);
    }

    @Override
    public void removeChild(MerkleLeaf oldChild) {
        if (lft == oldChild)
            parent.replaceChild(this, rgt);
        else if (rgt == oldChild)
            parent.replaceChild(this, lft);
    }

    @Override
    public void setParent(ParentMerkleNode parent) {
        this.parent = parent;
    }
}
