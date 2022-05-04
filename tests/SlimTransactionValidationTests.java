package tests;

import catecoin.txs.SlimTransaction;
import catecoin.utxos.SlimUTXO;
import catecoin.utxos.SlimUTXOIndependentFields;
import main.CryptographicUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.security.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SlimTransactionValidationTests {

    private final KeyPair sender = CryptographicUtils.generateECDSAKeyPair();

    private final KeyPair receiver = CryptographicUtils.generateECDSAKeyPair();

    public SlimTransactionValidationTests() throws Exception {}

    @Test
    void testSuccessfulTransaction() throws Exception {
        SlimTransaction tx = getDefaultTransaction();
        assertTrue(tx.hasValidSemantics());
    }

    @Test
    void testEmptyInput() throws Exception {
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver.getPublic(), Collections.emptyList(),
                List.of(1,2,3), List.of(3,4), sender.getPrivate());
        assertFalse(tx.hasValidSemantics());
    }

    @Test
    void testEmptyOutputDest() throws Exception {
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver.getPublic(), List.of(UUID.randomUUID()),
                Collections.emptyList(), List.of(3,4), sender.getPrivate());
        assertFalse(tx.hasValidSemantics());
    }

    @Test
    void testEmptyOutputOrig() throws Exception {
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver.getPublic(), List.of(UUID.randomUUID()),
                List.of(1,2,3), Collections.emptyList(), sender.getPrivate());
        assertTrue(tx.hasValidSemantics());
    }

    @Test
    void testForgedInput() throws Exception {
        SlimTransaction og = getDefaultTransaction();
        List<SlimUTXOIndependentFields> destinationFields = getFields(og.getOutputsDestination());
        List<SlimUTXOIndependentFields> originFields = getFields(og.getOutputsOrigin());
        SlimTransaction forged = new SlimTransaction(og.getOrigin(), og.getDestination(), List.of(UUID.randomUUID()),
                destinationFields, originFields, og.getOriginSignature());
        assertFalse(forged.hasValidSemantics());
    }

    @Test
    void testForgedOutputDest() throws Exception {
        SlimTransaction og = getDefaultTransaction();
        SlimUTXO addedOutput = new SlimUTXO(10, og.getOrigin(), og.getDestination());
        List<SlimUTXO> forgedOutputsDestination = new ArrayList<>(og.getOutputsDestination());
        forgedOutputsDestination.add(addedOutput);
        List<SlimUTXOIndependentFields> forgedDestFields = getFields(forgedOutputsDestination);
        List<SlimUTXOIndependentFields> originFields = getFields(og.getOutputsOrigin());
        SlimTransaction forged = new SlimTransaction(og.getOrigin(), og.getDestination(), og.getInputs(),
                forgedDestFields, originFields, og.getOriginSignature());
        assertFalse(forged.hasValidSemantics());
    }

    @Test
    void testForgedOutputOrig() throws Exception {
        SlimTransaction og = getDefaultTransaction();
        SlimUTXO addedOutput = new SlimUTXO(10, og.getOrigin(), og.getOrigin());
        List<SlimUTXO> forgedOutputsOrigin = new ArrayList<>(og.getOutputsDestination());
        forgedOutputsOrigin.add(addedOutput);
        List<SlimUTXOIndependentFields> destinationFields = getFields(og.getOutputsDestination());
        List<SlimUTXOIndependentFields> forgedOrigFields = getFields(forgedOutputsOrigin);
        SlimTransaction forged = new SlimTransaction(og.getOrigin(), og.getDestination(), og.getInputs(),
                destinationFields, forgedOrigFields, og.getOriginSignature());
        assertFalse(forged.hasValidSemantics());
    }

    @Test
    void testForgedSignature() throws Exception {
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver.getPublic(),
                List.of(UUID.randomUUID()), List.of(1,2,3), List.of(2,3), receiver.getPrivate());
        assertFalse(tx.hasValidSemantics());
    }

    @Test
    void testRepeatedInput() throws Exception {
        UUID input = UUID.randomUUID();
        List<UUID> inputs = List.of(input, input);
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver.getPublic(), inputs,
                List.of(1,2,3), List.of(2,3), sender.getPrivate());
        assertFalse(tx.hasValidSemantics());
    }

    @Test
    void testRepeatedOutputDest() throws Exception {
        SlimTransaction og = getDefaultTransaction();
        SlimUTXO repeat = og.getOutputsDestination().get(0);
        List<SlimUTXO> forgedOutputDest = new ArrayList<>(og.getOutputsDestination());
        forgedOutputDest.add(repeat);
        List<SlimUTXOIndependentFields> forgedDestFields = getFields(forgedOutputDest);
        List<SlimUTXOIndependentFields> originFields = getFields(og.getOutputsOrigin());
        SlimTransaction forged = new SlimTransaction(sender.getPublic(), receiver.getPublic(), sender.getPrivate(),
                og.getInputs(), forgedDestFields, originFields);
        assertFalse(forged.hasValidSemantics());
    }

    @Test
    void testRepeatedOutputOrig() throws Exception {
        SlimTransaction og = getDefaultTransaction();
        SlimUTXO repeat = og.getOutputsOrigin().get(0);
        List<SlimUTXO> forgedOutputOrig = new ArrayList<>(og.getOutputsOrigin());
        forgedOutputOrig.add(repeat);
        List<SlimUTXOIndependentFields> destinationFields = getFields(og.getOutputsDestination());
        List<SlimUTXOIndependentFields> forgedOrigFields = getFields(forgedOutputOrig);
        SlimTransaction forged = new SlimTransaction(sender.getPublic(), receiver.getPublic(), sender.getPrivate(),
                og.getInputs(), destinationFields, forgedOrigFields);
        assertFalse(forged.hasValidSemantics());
    }

    private SlimTransaction getDefaultTransaction() throws Exception {
        return new SlimTransaction(sender.getPublic(), receiver.getPublic(),
                List.of(UUID.randomUUID()), List.of(1,2,3), List.of(2,3), sender.getPrivate());
    }

    private List<SlimUTXOIndependentFields> getFields(List<SlimUTXO> utxos) {
        return utxos.stream().map(SlimUTXO::getIndependentFields).collect(Collectors.toList());
    }
}
