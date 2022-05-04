package utils.merkleTree;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static utils.CryptographicUtils.HASH_ALGORITHM;

public class MerkleRoot implements ParentMerkleNode, MerkleTree {

    /**
     * Minimum number of nodes that must be processed by a thread.
     * The number of threads used will be limited by this value.
     */
    private static final int CUTOFF_POINT = 10000;

    private final Map<ArrayWrapper, MerkleLeaf> leaves = new HashMap<>();

    private final List<MerkleNode> firstLevel;

    private byte[] hashValue = new byte[0];

    private boolean isModified = true;

    private int addRoundRobin;

    public MerkleRoot(List<byte[]> lowerHashVals) {
        this(lowerHashVals, CUTOFF_POINT);
    }

    public MerkleRoot(List<byte[]> lowerHashVals, int cutoffPoint) {
        if (!lowerHashVals.isEmpty()) {
            int numDirectChildren = Math.min(Runtime.getRuntime().availableProcessors(),
                    (lowerHashVals.size() / cutoffPoint) + 1);
            this.firstLevel = new ArrayList<>(numDirectChildren);
            int intervalLen = lowerHashVals.size() / numDirectChildren;
            for (int i = 0; i < numDirectChildren; i++)
                firstLevel.add(getNodeFromInterval(lowerHashVals, i, intervalLen));
            addRoundRobin = numDirectChildren - 1;
        } else
            firstLevel = new ArrayList<>(0);
    }

    private MerkleNode getNodeFromInterval(List<byte[]> hashVals, int intervalIndex, int intervalLen) {
        int lowerBound = intervalIndex * intervalLen;
        int upperBound = (intervalIndex + 1) * intervalLen;
        List<byte[]> subHashVals = hashVals.subList(lowerBound, upperBound);
        return subHashVals.size() == 1 ?
                new MerkleLeaf(subHashVals.get(0), this) :
                new InnerNode(subHashVals, this);
    }

    @Override
    public void percolateChange() {
        isModified = true;
    }

    @Override
    public void notifyLeafRef(MerkleLeaf leaf) {
        isModified = true;
        leaves.put(new ArrayWrapper(leaf.getHashValue()), leaf);
    }

    @Override
    public void replaceChild(MerkleNode oldChild, MerkleNode newChild) {
        for (int i = 0; i < firstLevel.size(); i++)
            if (oldChild == firstLevel.get(i)) {
                firstLevel.remove(i);
                firstLevel.add(i, newChild);
                isModified = true;
                break;
            }
    }

    @Override
    public void removeChild(MerkleLeaf oldChild) {
        for (int i = 0; i < firstLevel.size(); i++)
            if (oldChild == firstLevel.get(i)) {
                firstLevel.remove(i);
                leaves.remove(new ArrayWrapper(oldChild.getHashValue()));
                break;
            }
    }

    @Override
    public byte[] getHashValue() {
        hashValue = isModified ? computeHashValue() : hashValue;
        isModified = false;
        return hashValue;
    }

    private byte[] computeHashValue() {
        try {
            return tryToComputeHashValue();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private byte[] tryToComputeHashValue() throws NoSuchAlgorithmException {
        List<byte[]> firstLevelHashVals = computeFirstLevelHash();
        if (firstLevelHashVals.size() == 1)
            return firstLevelHashVals.get(0);
        return mergeFirstLevelHashes(firstLevelHashVals);
    }

    private byte[] mergeFirstLevelHashes(List<byte[]> firstLevelHashVals) throws NoSuchAlgorithmException {
        byte[] allHashesConcatenated = concatHashes(firstLevelHashVals);
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(allHashesConcatenated);
    }

    private List<byte[]> computeFirstLevelHash() {
        return firstLevel
                .parallelStream()
                .map(MerkleNode::getHashValue)
                .collect(toList());
    }

    private byte[] concatHashes(List<byte[]> seperateHashes) {
        int fullLen = seperateHashes.stream().mapToInt(b -> b.length).sum();
        byte[] fullHash = new byte[fullLen];
        int currPos = 0;
        for (byte[] hash : seperateHashes) {
            System.arraycopy(hash, 0, fullHash, currPos, hash.length);
            currPos += hash.length;
        }
        return fullHash;
    }

    @Override
    public void addLeaf(byte[] hashVal) {
        MerkleNode subtree = this.firstLevel.get(addRoundRobin);
        subtree.addLeaf(hashVal);
        addRoundRobin = (addRoundRobin + 1) % firstLevel.size();
    }

    @Override
    public boolean removeLeaf(byte[] hashVal) {
        MerkleLeaf leaf = leaves.remove(new ArrayWrapper(hashVal));
        return leaf != null && leaf.removeLeaf(hashVal);
    }

    @Override
    public boolean replaceLeaf(byte[] oldVal, byte[] newVal) {
        MerkleLeaf leaf = leaves.get(new ArrayWrapper(oldVal));
        if (leaf != null && leaf.replaceLeaf(oldVal, newVal)) {
            leaves.remove(new ArrayWrapper(oldVal));
            leaves.put(new ArrayWrapper(newVal), leaf);
            return true;
        }
        return false;
    }

    @Override
    public Set<byte[]> getLeaves() {
        return leaves.keySet().stream()
                .map(ArrayWrapper::getArray)
                .collect(toSet());
    }
}
