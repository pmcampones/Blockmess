package utils.merkleTree;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Maintains a consistent order in the nodes of the MerkleTree
 * so that a newly created MerkleTree with some given leaves
 * produces the same results as another MerkleTree that has the same
 * leaves, but has at some point had leaves inserted and removed.
 * <p>The aforementioned property is achieved by recreating the tree
 * at every insertion and removal.
 * For this reason this implementation is inefficient when insertions or
 * removals are frequent.</p>
 * <p>Nevertheless replacements are fast, with only a slight overhead when compared
 * with the implementation not boosting this consistent order property.</p>
 */
public class ConsistentOrderMerkleTree implements MerkleTree {

    private MerkleTree inner;

    private LinkedHashSet<ArrayWrapper> leavesSet;

    public ConsistentOrderMerkleTree(List<byte[]> leaves) {
        inner = new MerkleRoot(leaves);
        leavesSet = new LinkedHashSet<>(leaves.size());
        leaves.stream().map(ArrayWrapper::new).forEach(leavesSet::add);
    }

    @Override
    public byte[] getHashValue() {
        return inner.getHashValue();
    }

    @Override
    public void addLeaf(byte[] hashVal) {
        leavesSet.add(new ArrayWrapper(hashVal));
        inner = makeInnerFromLeavesSet();
    }

    @Override
    public boolean removeLeaf(byte[] hashVal) {
        boolean found = leavesSet.remove(new ArrayWrapper(hashVal));
        if (found)
            inner = makeInnerFromLeavesSet();
        return found;
    }

    @NotNull
    private MerkleRoot makeInnerFromLeavesSet() {
        return new MerkleRoot(leavesSet.stream().map(ArrayWrapper::getArray).collect(toList()));
    }

    @Override
    public boolean replaceLeaf(byte[] oldVal, byte[] newVal) {
        if (leavesSet.contains(new ArrayWrapper(oldVal))) {
            inner.replaceLeaf(oldVal, newVal);
            leavesSet = replaceInLeavesSet(oldVal, newVal);
            return true;
        }
        return false;
    }

    @Override
    public Set<byte[]> getLeaves() {
        return leavesSet.stream()
                .map(ArrayWrapper::getArray)
                .collect(toSet());
    }

    private LinkedHashSet<ArrayWrapper> replaceInLeavesSet(byte[] oldVal, byte[] newVal) {
        List<ArrayWrapper> leavesList = new ArrayList<>(leavesSet.size());
        Iterator<ArrayWrapper> it = leavesSet.iterator();
        ArrayWrapper l = it.next();
        while(!Arrays.equals(oldVal, l.getArray())) {
            leavesList.add(l);
            l = it.next();
        }
        leavesList.add(new ArrayWrapper(newVal));
        while (it.hasNext())
            leavesList.add(it.next());
        return new LinkedHashSet<>(leavesList);
    }
}
