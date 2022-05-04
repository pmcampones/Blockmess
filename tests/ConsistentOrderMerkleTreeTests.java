package tests;

import org.apache.commons.collections4.SetUtils;
import org.junit.jupiter.api.RepeatedTest;
import utils.merkleTree.ArrayWrapper;
import utils.merkleTree.ConsistentOrderMerkleTree;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

public class ConsistentOrderMerkleTreeTests {

    @RepeatedTest(100)
    void shouldHaveSameKeysAsDefaultMerkleTreeImplementation() {
        List<byte[]> leaves = getLeaves(1);
        MerkleTree consistent = new ConsistentOrderMerkleTree(leaves);
        MerkleTree regular = new MerkleRoot(leaves);
        assertConsistentLeaves(consistent, regular);
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSingleAddition() {
        shouldHaveSameLeavesOnGivenAdditions(1);
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSingleAddition() {
        shouldMaintainCorrectResultOnGivenAdditions(1);
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSingleRemoval() {
        shouldHaveSameLeavesOnGivenRemovals(1);
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSingleRemoval() {
        shouldMaintainCorrectResultOnGivenRemovals(1);
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSingleReplacement() {
        shouldHaveSameLeavesOnGivenReplacements(1);
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSingleReplacement() {
        shouldMaintainsCorrectResultOnGivenReplacements(1);
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSeveralAdditions() {
        shouldHaveSameLeavesOnGivenAdditions(100);
    }

    private void shouldHaveSameLeavesOnGivenAdditions(int additions) {
        List<byte[]> leaves = getLeaves(1);
        MerkleTree consistent = new ConsistentOrderMerkleTree(leaves);
        MerkleTree regular = new MerkleRoot(leaves);
        for (int i = 0; i < additions; i++)
            addLeafTwoTrees(consistent, regular);
        assertConsistentLeaves(consistent, regular);
    }

    private void addLeafTwoTrees(MerkleTree consistent, MerkleTree regular) {
        byte[] newLeaf = generateRandomArray();
        consistent.addLeaf(newLeaf.clone());
        regular.addLeaf(newLeaf.clone());
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSeveralAdditions() {
        shouldMaintainCorrectResultOnGivenAdditions(100);
    }

    private void shouldMaintainCorrectResultOnGivenAdditions(int additions) {
        List<byte[]> leaves = getLeaves(1);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        updatable.getHashValue();
        for (int i = 0; i < additions; i++)
            addLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    private void addLeaf(List<byte[]> leaves, MerkleTree updatable) {
        byte[] addedLeaf = generateRandomArray();
        updatable.addLeaf(addedLeaf.clone());
        leaves.add(addedLeaf);
        updatable.getHashValue();
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSeveralRemovals() {
        shouldHaveSameLeavesOnGivenRemovals(100);
    }

    private void shouldHaveSameLeavesOnGivenRemovals(int removals) {
        List<byte[]> leaves = getLeaves(removals + 1);
        MerkleTree consistent = new ConsistentOrderMerkleTree(leaves);
        MerkleTree regular = new MerkleRoot(leaves);
        for (int i = 0; i < removals; i++)
            removeLeafTwoTrees(leaves, consistent, regular);
        assertConsistentLeaves(consistent, regular);
    }

    private void removeLeafTwoTrees(List<byte[]> leaves, MerkleTree consistent, MerkleTree regular) {
        byte[] newLeaf = leaves.remove(new Random().nextInt(leaves.size()));
        boolean removedConsistent = consistent.removeLeaf(newLeaf.clone());
        assertTrue(removedConsistent);
        boolean removedRegular = regular.removeLeaf(newLeaf.clone());
        assertTrue(removedRegular);
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSeveralRemovals() {
        shouldMaintainCorrectResultOnGivenRemovals(100);
    }

    private void shouldMaintainCorrectResultOnGivenRemovals(int removals) {
        List<byte[]> leaves = getLeaves(removals + 1);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < removals; i++)
            removeLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    private void removeLeaf(List<byte[]> leaves, MerkleTree updatable) {
        byte[] removedLeaf = leaves.remove(new Random().nextInt(leaves.size()));
        boolean removedTree = updatable.removeLeaf(removedLeaf.clone());
        assertTrue(removedTree);
        leaves.remove(removedLeaf);
        updatable.getHashValue();
    }

    @RepeatedTest(100)
    void shouldHaveSameLeavesOnSeveralReplacements() {
        shouldHaveSameLeavesOnGivenReplacements(100);
    }

    private void shouldHaveSameLeavesOnGivenReplacements(int replacements) {
        List<byte[]> leaves = getLeaves(replacements / 2);
        MerkleTree consistent = new ConsistentOrderMerkleTree(leaves);
        MerkleTree regular = new MerkleRoot(leaves);
        for (int i = 0; i < replacements; i++)
            replaceLeafOnTwoTrees(leaves, consistent, regular);
        assertConsistentLeaves(consistent, regular);
    }

    private void replaceLeafOnTwoTrees(List<byte[]> leaves, MerkleTree consistent, MerkleTree regular) {
        byte[] newLeaf = generateRandomArray();
        int index = new Random().nextInt(leaves.size());
        byte[] oldLeaf = leaves.remove(index);
        leaves.add(index, newLeaf);
        boolean replacedConsistent = consistent.replaceLeaf(oldLeaf.clone(), newLeaf.clone());
        assertTrue(replacedConsistent);
        boolean replacedRegular = regular.replaceLeaf(oldLeaf.clone(), newLeaf.clone());
        assertTrue(replacedRegular);
    }

    @RepeatedTest(100)
    void shouldMaintainCorrectResultOnSeveralReplacements() {
        shouldMaintainsCorrectResultOnGivenReplacements(100);
    }

    private void shouldMaintainsCorrectResultOnGivenReplacements(int replacements) {
        List<byte[]> leaves = getLeaves(replacements / 2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < replacements; i++)
            replaceLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    private void replaceLeaf(List<byte[]> leaves, MerkleTree updatable) {
        byte[] newVal = generateRandomArray();
        int index = new Random().nextInt(leaves.size());
        byte[] oldVal = leaves.remove(index);
        assertNotNull(oldVal);
        boolean hasReplaced = updatable.replaceLeaf(oldVal.clone(), newVal.clone());
        assertTrue(hasReplaced);
        leaves.add(index, newVal);
        updatable.getHashValue();
    }

    @RepeatedTest(100)
    void shouldAllowAdditionFollowedByRemoval() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        addLeaf(leaves, updatable);
        removeLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowRemovalFollowedByAddition() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        removeLeaf(leaves, updatable);
        addLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowAdditionFollowedByReplacement() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        addLeaf(leaves, updatable);
        replaceLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowReplacementFollowedByAddition() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        replaceLeaf(leaves, updatable);
        addLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowRemovalFollowedByReplacement() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        removeLeaf(leaves, updatable);
        replaceLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowReplacementFollowedByRemoval() {
        List<byte[]> leaves = getLeaves(2);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        replaceLeaf(leaves, updatable);
        removeLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralAdditionsFollowedByRemovals() {
        List<byte[]> leaves = getLeaves(1);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            addLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            removeLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralRemovalsFollowedByAdditions() {
        List<byte[]> leaves = getLeaves(101);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            removeLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            addLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralAdditionsFollowedByReplacements() {
        List<byte[]> leaves = getLeaves(1);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            addLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            replaceLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralReplacementsFollowedByAdditions() {
        List<byte[]> leaves = getLeaves(50);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            replaceLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            addLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralRemovalsFollowedByReplacements() {
        List<byte[]> leaves = getLeaves(100);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            removeLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            replaceLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowSeveralReplacementsFollowedByRemovals() {
        List<byte[]> leaves = getLeaves(100);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++)
            replaceLeaf(leaves, updatable);
        for (int i = 0; i < 100; i++)
            removeLeaf(leaves, updatable);
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowIntermittentAdditionsAndRemovals() {
        List<byte[]> leaves = getLeaves(100);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 200; i++) {
            addLeaf(leaves, updatable);
            removeLeaf(leaves, updatable);
        }
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowIntermittentAdditionsAndReplacements() {
        List<byte[]> leaves = getLeaves(100);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 100; i++) {
            addLeaf(leaves, updatable);
            replaceLeaf(leaves, updatable);
        }
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(100)
    void shouldAllowIntermittentRemovalsAndReplacements() {
        List<byte[]> leaves = getLeaves(300);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 200; i++) {
            removeLeaf(leaves, updatable);
            replaceLeaf(leaves, updatable);
        }
        assertConsistentResults(leaves, updatable);
    }

    @RepeatedTest(20)
    void shouldMaintainCorrectResultOnSeveralRandomChanges() {
        List<byte[]> leaves = getLeaves(1001);
        MerkleTree updatable = new ConsistentOrderMerkleTree(leaves);
        for (int i = 0; i < 1000; i++) {
            int operation = new Random().nextInt(3);
            switch (operation) {
                case 0:
                    addLeaf(leaves, updatable);
                    break;
                case 1:
                    removeLeaf(leaves, updatable);
                    break;
                case 2:
                    replaceLeaf(leaves, updatable);
                    break;
            }
        }
        assertConsistentResults(leaves, updatable);
    }

    private void assertConsistentLeaves(MerkleTree consistent, MerkleTree regular) {
        Set<ArrayWrapper> consistentLeaves = consistent.getLeaves().stream().map(ArrayWrapper::new).collect(toSet());
        Set<ArrayWrapper> regularLeaves = regular.getLeaves().stream().map(ArrayWrapper::new).collect(toSet());
        SetUtils.isEqualSet(consistentLeaves, regularLeaves);
    }

    private void assertConsistentResults(List<byte[]> leaves, MerkleTree updatable) {
        MerkleTree constant = new ConsistentOrderMerkleTree(leaves);
        assertArrayEquals(constant.getHashValue(), updatable.getHashValue());
    }

    private List<byte[]> getLeaves(int offset) {
        offset = Math.max(offset, 1);
        List<byte[]> leaves = new LinkedList<>();
        int numLeaves = new Random().nextInt(500) + offset;
        for (int i = 0; i < numLeaves; i++)
            leaves.add(generateRandomArray());
        return leaves;
    }

    private byte[] generateRandomArray() {
        byte[] array = new byte[16];
        new Random().nextBytes(array);
        return array;
    }


}
