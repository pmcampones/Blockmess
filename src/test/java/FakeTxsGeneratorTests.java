package test.java;

import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.SlimUTXO;
import main.CryptographicUtils;
import org.junit.jupiter.api.Test;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FakeTxsGeneratorTests {

    @Test
    void shouldSendTxsToEveryDestination() throws Exception {
        List<PublicKey> destinations = computeDestinations();
        Collection<SlimTransaction> txs = genTxs(destinations);
        for (PublicKey dest : destinations) {
            assertTrue(txs.stream()
                    .map(SlimTransaction::getDestination)
                    .anyMatch(d -> d.equals(dest)));
        }
    }

    @Test
    void shouldGenerateValidTxs() throws Exception {
        List<PublicKey> destinations = computeDestinations();
        Collection<SlimTransaction> txs = genTxs(destinations);
        assertTrue(txs.stream()
                .parallel()
                .allMatch(SlimTransaction::hasValidSemantics));
    }

    @Test
    void shouldGenTxsWithDistinctIds() throws Exception {
        List<PublicKey> destinations = computeDestinations();
        Collection<SlimTransaction> txs = genTxs(destinations);
        assertEquals(txs.size(),
                txs.stream()
                .map(SlimTransaction::getId)
                .distinct().count());
    }

    @Test
    void shouldGenTxsWithDistinctUtxos() throws Exception {
        List<PublicKey> destinations = computeDestinations();
        Collection<SlimTransaction> txs = genTxs(destinations);
        int numOutputs = computeNumOutputs(txs);
        int numDistinct = computeNumDistinctOutputs(txs);
        assertEquals(numOutputs, numDistinct);
    }

    private int computeNumOutputs(Collection<SlimTransaction> txs) {
        return (int) getOutputs(txs).count();
    }

    private int computeNumDistinctOutputs(Collection<SlimTransaction> txs) {
        return (int) getOutputs(txs).map(SlimUTXO::getId).distinct().count();
    }

    private Stream<SlimUTXO> getOutputs(Collection<SlimTransaction> txs) {
        return Stream.concat(
                txs.stream().map(SlimTransaction::getOutputsDestination).flatMap(Collection::stream),
                txs.stream().map(SlimTransaction::getOutputsOrigin).flatMap(Collection::stream));
    }

    private List<PublicKey> computeDestinations()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        int numDest = 20;
        List<PublicKey> destinations = new ArrayList<>(numDest);
        for (int i = 0; i < numDest; i++) {
            destinations.add(CryptographicUtils.generateECDSAKeyPair().getPublic());
        }
        return destinations;
    }

    private Collection<SlimTransaction> genTxs(List<PublicKey> destinations) throws Exception {
        KeyPair txGenKeys = CryptographicUtils.generateECDSAKeyPair();
        long begin = System.currentTimeMillis();
        Collection<SlimTransaction> txs = new FakeTxsGenerator(txGenKeys)
                .generateFakeTxs(destinations, 10000);
        long end = System.currentTimeMillis();
        System.out.println("Elapsed Time: " + (end - begin));
        return txs;
    }

}
