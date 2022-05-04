package tests;

import main.CryptographicUtils;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MerkleTreeTests {

    @Test
    void shouldComputeCorrectForSingleHash() {
        byte[] sample = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(sample));
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(sample, treeHash);
    }

    @Test
    void shouldComputeCorrectForTwoHashes() throws Exception {
        byte[] lftSample = new byte[]{1,2,3,4,5};
        byte[] rgtSample = new byte[]{6,7,8,9,10};
        byte[] expected = mergeHashes(lftSample, rgtSample);
        MerkleTree tree = new MerkleRoot(List.of(lftSample, rgtSample));
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(expected, treeHash);
    }

    /**
     * In case of an odd number of samples, the results must be computed from left to right.
     */
    @Test
    void shouldComputeCorrectForOddHashes() throws Exception {
        byte[] lftSample = new byte[]{1,2,3,4,5};
        byte[] midSample = new byte[]{6,7,8,9,10};
        byte[] rightSample = new byte[]{11,12,13,14,15};
        byte[] midRes = mergeHashes(midSample, rightSample);
        byte[] finalHash = mergeHashes(lftSample, midRes);
        MerkleTree tree = new MerkleRoot(List.of(lftSample, midSample, rightSample));
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(finalHash, treeHash);
    }

    /**
     * More than the Cut off constant.
     * Allows the computation on two threads.
     */
    @Test
    void shouldComputeCorrectFor16Hashes() throws Exception {
        byte[] res0000 = new byte[]{1,2,3,4,5};
        byte[] res0001 = new byte[]{6,7,8,9,10};
        byte[] res0002 = new byte[]{11,12,13,14,15};
        byte[] res0003 = new byte[]{16,17,18,19,20};
        byte[] res0004 = new byte[]{21,22,23,24,25};
        byte[] res0005 = new byte[]{26,27,28,29,30};
        byte[] res0006 = new byte[]{31,32,33,34,35};
        byte[] res0007 = new byte[]{36,37,38,39,40};
        byte[] res0008 = new byte[]{41,42,43,44,45};
        byte[] res0009 = new byte[]{46,47,48,49,50};
        byte[] res000A = new byte[]{51,52,53,54,55};
        byte[] res000B = new byte[]{56,57,58,59,60};
        byte[] res000C = new byte[]{61,62,63,64,65};
        byte[] res000D = new byte[]{66,67,68,69,70};
        byte[] res000E = new byte[]{71,72,73,74,75};
        byte[] res000F = new byte[]{76,77,78,79,80};

        byte[] res000 = mergeHashes(res0000, res0001);
        byte[] res001 = mergeHashes(res0002, res0003);
        byte[] res002 = mergeHashes(res0004, res0005);
        byte[] res003 = mergeHashes(res0006, res0007);
        byte[] res004 = mergeHashes(res0008, res0009);
        byte[] res005 = mergeHashes(res000A, res000B);
        byte[] res006 = mergeHashes(res000C, res000D);
        byte[] res007 = mergeHashes(res000E, res000F);

        byte[] res00 = mergeHashes(res000, res001);
        byte[] res01 = mergeHashes(res002, res003);
        byte[] res02 = mergeHashes(res004, res005);
        byte[] res03 = mergeHashes(res006, res007);

        byte[] res0 = mergeHashes(res00, res01);
        byte[] res1 = mergeHashes(res02, res03);

        byte[] res = mergeHashes(res0, res1);
        MerkleTree tree = new MerkleRoot(List.of(
                res0000, res0001, res0002, res0003, res0004, res0005, res0006, res0007,
                res0008, res0009, res000A, res000B, res000C, res000D, res000E, res000F));
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(res, treeHash);
    }

    @Test
    void shouldHaveASingleLeaf() {
        byte[] og = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(og.clone()));
        assertEquals(1, tree.getLeaves().size());
        assertArrayEquals(og.clone(), tree.getLeaves().iterator().next());
    }

    @Test
    void shouldReplaceLeavesSetOnAddition() {
        byte[] og = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(og.clone()));
        byte[] added = genRandomByteArray(10);
        tree.addLeaf(added.clone());
        assertEquals(2, tree.getLeaves().size());
    }

    @Test
    void shouldReplaceLeavesSetOnRemoval() {
        byte[] og = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(og.clone()));
        tree.removeLeaf(og.clone());
        assertTrue(tree.getLeaves().isEmpty());
    }

    @Test
    void shouldReplaceLeavesSetOnReplacement() {
        byte[] og = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(og.clone()));
        byte[] revised = genRandomByteArray(10);
        tree.replaceLeaf(og.clone(), revised.clone());
        Set<byte[]> leaves = tree.getLeaves();
        assertEquals(1, leaves.size());
        assertArrayEquals(revised.clone(), leaves.iterator().next().clone());
    }

    @Test
    void shouldAllowChangeSingleHash() {
        byte[] originalSample = new byte[]{1,2,3,4,5};
        MerkleTree tree = new MerkleRoot(List.of(originalSample.clone()));
        tree.getHashValue();
        byte[] revisedSample = new byte[]{6,7,8,9,10};
        tree.replaceLeaf(originalSample.clone(), revisedSample.clone());
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(revisedSample, treeHash);
    }

    @Test
    void shouldAllowChangeLeft() throws Exception {
        byte[] originalLft = new byte[]{1,2,3,4};
        byte[] rgt = new byte[]{5,6,7,8};
        MerkleTree tree = new MerkleRoot(List.of(originalLft, rgt));
        tree.getHashValue();
        byte[] revisedLft = new byte[]{9,10,11,12};
        byte[] expected = mergeHashes(revisedLft, rgt);
        byte[] originalCpy = new byte[]{1,2,3,4};
        tree.replaceLeaf(originalCpy, revisedLft);
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(expected, treeHash);
    }

    @Test
    void shouldAllowChangeRight() throws Exception {
        byte[] lft = new byte[]{1,2,3,4};
        byte[] originalRgt = new byte[]{5,6,7,8};
        MerkleTree tree = new MerkleRoot(List.of(lft, originalRgt));
        tree.getHashValue();
        byte[] revisedRgt = new byte[]{9,10,11,12};
        byte[] expected = mergeHashes(lft, revisedRgt);
        byte[] originalCpy = new byte[]{5,6,7,8};
        tree.replaceLeaf(originalCpy, revisedRgt);
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(expected, treeHash);
    }

    @Test
    void shouldAllowAddNewLeaf() throws Exception {
        byte[] firstSample = new byte[]{1,2,3,4};
        byte[] secondSample = new byte[]{5,6,7,8};
        byte[] expected = mergeHashes(firstSample, secondSample);
        MerkleTree tree = new MerkleRoot(List.of(firstSample));
        tree.getHashValue();
        tree.addLeaf(secondSample);
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(expected, treeHash);
    }

    @Test
    void shouldAllowAddOnEvenTree() throws Exception {
        byte[] leftSample = new byte[]{1,2,3,4};
        byte[] rightSample = new byte[]{5,6,7,8};
        byte[] extraSample = new byte[]{9,10,11,12};
        byte[] mid = mergeHashes(leftSample, extraSample);
        byte[] expected = mergeHashes(mid, rightSample);
        MerkleTree tree = new MerkleRoot(List.of(leftSample, rightSample));
        tree.getHashValue();
        tree.addLeaf(extraSample);
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(expected, treeHash);
    }

    @Test
    void shouldRemoveLeafOnRemoval() {
        byte[] stay = genRandomByteArray(10);
        byte[] remove = genRandomByteArray(10);
        MerkleTree tree = new MerkleRoot(List.of(stay.clone(), remove.clone()));
        tree.removeLeaf(remove.clone());
        assertEquals(1, tree.getLeaves().size());
        assertArrayEquals(stay.clone(), tree.getLeaves().iterator().next());
        tree.removeLeaf(stay.clone());
        assertTrue(tree.getLeaves().isEmpty());
    }

    @Test
    void shouldRemoveLeafOnDepth0() {
        byte[] toRemove = new byte[]{1,2,3,4};
        MerkleTree tree = new MerkleRoot(List.of(toRemove.clone()));
        tree.getHashValue();
        tree.removeLeaf(toRemove.clone());
        assertTrue(tree.getLeaves().isEmpty());
    }

    @Test
    void shouldRemoveChildOnDepth1() {
        byte[] permanent = new byte[]{1,2,3,4};
        byte[] toRemove = new byte[]{5,6,7,8};
        MerkleTree tree = new MerkleRoot(List.of(permanent, toRemove));
        tree.getHashValue();
        tree.removeLeaf(toRemove.clone());
        byte[] treeHash = tree.getHashValue();
        assertArrayEquals(permanent, treeHash);
    }

    /* Emulates a situation found in a disparity between the GPoET and the GPoETValidator*/
    @RepeatedTest(100)
    void shouldAllowSeveralReplacements() {
        byte[] constVal = genRandomByteArray(256);
        byte[] first = genRandomByteArray(16);
        byte[] second = genRandomByteArray(256);
        MerkleTree updatable = new MerkleRoot(List.of(constVal, first, second));
        MerkleTree constant1 = new MerkleRoot(List.of(constVal.clone(), first.clone(), second.clone()));
        assertArrayEquals(constant1.getHashValue(), updatable.getHashValue());

        for (int i = 0; i < 100; i++) {
            byte[] firstUpdate = genRandomByteArray(16);
            byte[] secondUpdate = genRandomByteArray(256);
            updatable.replaceLeaf(first.clone(), firstUpdate.clone());
            updatable.replaceLeaf(second.clone(), secondUpdate.clone());
            MerkleTree constant = new MerkleRoot(List.of(constVal.clone(), firstUpdate.clone(), secondUpdate.clone()));
            assertArrayEquals(constant.getHashValue(), updatable.getHashValue());
            first = firstUpdate.clone();
            second = secondUpdate.clone();
        }
    }

    private byte[] genRandomByteArray(int length) {
        byte[] array = new byte[length];
        new Random().nextBytes(array);
        return array;
    }

    private byte[] mergeHashes(byte[] lft, byte[] rgt) throws NoSuchAlgorithmException {
        byte[] both = new byte[lft.length + rgt.length];
        System.arraycopy(lft, 0, both, 0, lft.length);
        System.arraycopy(rgt, 0, both, lft.length, rgt.length);
        return MessageDigest.getInstance(CryptographicUtils.HASH_ALGORITHM).digest(both);
    }

}
