package utils.merkleTree;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import main.CryptographicUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MerkleTreeBenchmark {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        testDifferentCutoffPoints();
        testStoresPrevInfo();
    }

    /**
     * The hash operations are so efficient that it's very difficult to identify which is the ideal cutoff point
     * for the number of leaves to be handled by a single thread.
     * <p>Nevertheless, for practical purposes of the merkle tree's use in this application,
     * a single thread seems to be the ideal.</p>
     */
    private static void testDifferentCutoffPoints() {
        int increaseNumNodes = 10000;
        for (int numNodes = increaseNumNodes; numNodes < 11 * increaseNumNodes; numNodes += increaseNumNodes) {
            List<byte[]> leafNodesHash = genLeafHashes(numNodes);
            for (int numThreads = 1; numThreads <= Runtime.getRuntime().availableProcessors(); numThreads++)
                processTimeRequiredToComputeMerkleHash(leafNodesHash, numThreads);
        }
    }

    private static void processTimeRequiredToComputeMerkleHash(List<byte[]> hashVals, int numThreads) {
        List<Long> measurements = measureTimeRequiredToComputeMerkleHash(hashVals, hashVals.size() / numThreads);
        List<Long> sorted = measurements.stream().sorted().collect(Collectors.toList());
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double avg = measurements.stream().mapToLong(i -> i).average().getAsDouble();
        long median = sorted.get(sorted.size() / 2);
        System.out.printf("Merkle Tree computed hash of %d lower nodes with %d threads with the following metrics over %d measurements:\n" +
                "Min:%d, Max:%d, Avg:%.2f, Median:%d\n\n", hashVals.size(), numThreads, measurements.size(), min, max, avg, median);
    }

    private static List<Long> measureTimeRequiredToComputeMerkleHash(List<byte[]> hashVals, int cutoffPoint) {
        int numMeasurements = 30;
        List<Long> measurements = new ArrayList<>(numMeasurements);
        for (int i = 0; i < numMeasurements; i++) {
            long start = System.currentTimeMillis();
            MerkleTree tree = new MerkleRoot(hashVals, cutoffPoint);
            tree.getHashValue();
            long end = System.currentTimeMillis();
            measurements.add(end - start);
        }
        return measurements;
    }

    /**
     * Because the Merkle Tree performs theta(n log n) hashes,
     * while the naive recombination only does theta(n) recombination steps in the hash algorithm,
     * for a very large number of nodes (about a million) the naive solution performs better than the merkle tree.
     * <p>However, when a single leaf is modified in the tree, the number of hashes recomputed is theta(log n),
     * and so the computation is super fast.</p>
     */
    private static void testStoresPrevInfo() throws NoSuchAlgorithmException {
        int numNodes = 1000000;
        List<byte[]> leafs = genLeafHashes(numNodes);
        long startNaiveSolution = System.currentTimeMillis();
        computeNaiveByteListHash(leafs);
        long endNaiveSolution = System.currentTimeMillis();
        System.out.printf("Computed naive hash in %d miliseconds\n", endNaiveSolution - startNaiveSolution);
        long startFull = System.currentTimeMillis();
        MerkleTree tree = new MerkleRoot(leafs);
        tree.getHashValue();
        long endFull = System.currentTimeMillis();
        System.out.printf("Computed fresh merkle Hash in %d milliseconds\n", endFull - startFull);
        long startCached = System.currentTimeMillis();
        tree.getHashValue();
        long endCached = System.currentTimeMillis();
        System.out.printf("Computed cached merkle Hash in %d milliseconds\n", endCached - startCached);
        long startReplace = System.currentTimeMillis();
        tree.replaceLeaf(leafs.get(0), gen256BitArray());
        tree.getHashValue();
        long endReplace = System.currentTimeMillis();
        System.out.printf("Computed replace leaf merkle Hash in %d milliseconds\n", endReplace - startReplace);
        long startAdd = System.currentTimeMillis();
        tree.addLeaf(gen256BitArray());
        tree.getHashValue();
        long endAdd = System.currentTimeMillis();
        System.out.printf("Computed add leaf merkle Hash in %d milliseconds\n", endAdd - startAdd);
    }

    private static byte[] computeNaiveByteListHash(List<byte[]> hashVals) throws NoSuchAlgorithmException {
        ByteBuf byteBuf = Unpooled.buffer(hashVals.stream().mapToInt(b -> b.length).sum());
        hashVals.forEach(byteBuf::writeBytes);
        return MessageDigest.getInstance(CryptographicUtils.HASH_ALGORITHM).digest(byteBuf.array());
    }

    private static List<byte[]> genLeafHashes(int numNodes) {
        return IntStream.range(0, numNodes)
                .mapToObj(i -> gen256BitArray())
                .collect(Collectors.toList());
    }

    private static byte[] gen256BitArray() {
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        return b;
    }

}
