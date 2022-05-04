package tests;

import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import main.CryptographicUtils;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SlimTxSerializationTests {

    @Test
    void testSerializeEmptySlimTx() throws Exception {
        executeTestWithFields(emptyList(), emptyList(), emptyList());
    }

    @Test
    void testSerializeTxWithPrev() throws Exception {
        List<UUID> prev = genPrev();
        executeTestWithFields(prev, emptyList(), emptyList());
    }

    @Test
    void testSerializeTxWithOutputDestination() throws Exception {
        List<Integer> destinationAmounts = genOutputs();
        executeTestWithFields(emptyList(), destinationAmounts, emptyList());
    }

    @Test
    void testSerializeTxWithOutputOrigin() throws Exception {
        List<Integer> originAmounts = genOutputs();
        executeTestWithFields(emptyList(), emptyList(), originAmounts);
    }

    @Test
    void testSerializeFullTx() throws Exception {
        List<UUID> prev = genPrev();
        List<Integer> destinationAmounts = genOutputs();
        List<Integer> originAmounts = genOutputs();
        executeTestWithFields(prev, destinationAmounts, originAmounts);
    }

    static List<UUID> genPrev() {
        int numPrev = new Random().nextInt(15);
        return IntStream.range(0, numPrev)
                .mapToObj(i -> randomUUID()).collect(toList());
    }

    static List<Integer> genOutputs() {
        Random r = new Random();
        int numOutputs = r.nextInt(15);
        return IntStream.range(0, numOutputs)
                .mapToObj(i -> r.nextInt(100)).collect(toList());
    }

    private void executeTestWithFields(List<UUID> prev, List<Integer> destinationAmounts,
                                       List<Integer> originAmounts) throws Exception {
        KeyPair origin = CryptographicUtils.generateECDSAKeyPair();
        KeyPair destination = CryptographicUtils.generateECDSAKeyPair();
        SlimTransaction txOg = new SlimTransaction(origin.getPublic(), destination.getPublic(),
                prev, destinationAmounts, originAmounts, origin.getPrivate());
        ByteBuf byteBuf = Unpooled.buffer();
        SlimTransaction.serializer.serialize(txOg, byteBuf);
        SlimTransaction txCpy = (SlimTransaction) SlimTransaction.serializer.deserialize(byteBuf);
        assertEquals(txOg.getId(), txCpy.getId());
        assertEquals(txOg.getOrigin(), txCpy.getOrigin());
        assertEquals(txOg.getDestination(), txCpy.getDestination());
        assertEquals(txOg.getInputs(), txCpy.getInputs());
        assertEquals(txOg.getOutputsDestination(), txCpy.getOutputsDestination());
        assertEquals(txOg.getOutputsOrigin(), txCpy.getOutputsOrigin());
    }

}
