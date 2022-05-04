package utils.merkleTree;

public interface ParentMerkleNode {

    void percolateChange();

    /**
     * This will lead to a cascade of upward notifications with complexity theta(n log n).
     * <p>This could be reduced to theta(n) if the need (and the will to do so) arises.</p>
     */
    void notifyLeafRef(MerkleLeaf leaf);

    void replaceChild(MerkleNode oldChild, MerkleNode newChild);

    void removeChild(MerkleLeaf oldChild);

}
