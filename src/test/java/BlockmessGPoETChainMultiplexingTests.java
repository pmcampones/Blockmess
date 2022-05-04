package test.java;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BlockmessGPoETChainMultiplexingTests {

    @Test
    void shouldProvideAnAverageNumberOfChains() {
        int numChains = 20;
        int numSamples = 1000;
        double avg = 0;
        List<Integer> Chains = IntStream.range(0, numChains).boxed().collect(Collectors.toList());
        int[] ChainCount = new int[numChains];
        for (int i = 0; i < numSamples; i++) {
            byte[] solution = new byte[256];
            new Random().nextBytes(solution);
            Integer selected = multiplexChain(Chains, solution);
            ChainCount[selected]++;
            avg += selected;
        }
        avg /= numChains;
        System.out.println("Average result: " + avg);
        for (int i : ChainCount)
            System.out.println(i);

        System.out.println("\n");

        int min = Arrays.stream(ChainCount).min().orElse(0);
        for (int i : ChainCount)
            System.out.println(i - min);

    }

    private Integer multiplexChain(List<Integer> Chains, byte[] solution) {
        long lastInteger = Integer.toUnsignedLong(getLastInteger(solution));
        int numChains = Chains.size();
        long maxUnsignedInteger = 1L << Integer.SIZE;
        long ChainInterval = maxUnsignedInteger / numChains;
        long accum = 0;
        Integer currProof;
        Iterator<Integer> it = Chains.iterator();
        do {
            currProof = it.next();
            accum += ChainInterval;
        } while (lastInteger > accum);
        return currProof;
    }

    private int getLastInteger(byte[] array) {
        byte[] intArray = new byte[Integer.BYTES];
        for (int i = Integer.BYTES; i > 0; i--)
            intArray[Integer.BYTES - i] = array[array.length - i];
        ByteBuffer byteBuffer = ByteBuffer.wrap(intArray);
        return byteBuffer.getInt();
    }


}
