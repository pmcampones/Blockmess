package tests;

import catecoin.exceptions.NotEnoughCoinsException;
import catecoin.transactionGenerators.TransactionGenerator;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the class {@link TransactionGenerator}
 * The tests focus on the correctness of the class in receiving external information,
 *  and in generating correct transactions based on the UTXOs provided.
 * The correct transactions must use only the UTXOs available (not previously used),
 *  and must exchange the correct amount of coins, even if the available UTXOs' amounts do not match the request,
 */
public class TransactionGeneratorTests {

    private final KeyPair myKeys = CryptographicUtils.generateECDSAKeyPair();

    private final KeyPair otherKeys = CryptographicUtils.generateECDSAKeyPair();

    private final Properties props = Babel.loadConfig(new String[]{}, Main.DEFAULT_TEST_CONF);

    private final TransactionGenerator tg = new TransactionGenerator(props, myKeys);

    public TransactionGeneratorTests() throws Exception {}

    /**
     * Simulates the finalization of a block with some UTXOs destined to this node, and others that do not concern this node.
     * Tests whether this node correctly filters the received content.
     */
    @Test
    void testAddUTXODestination() throws Exception {
        SlimTransaction ignoredTx = new SlimTransaction(otherKeys.getPublic(), otherKeys.getPublic(),
                List.of(randomUUID()), List.of(1,2,3,4,5,6,7,8), emptyList(), otherKeys.getPrivate());
        Set<StorageUTXO> ignoredUTXOs = ignoredTx.getOutputsDestination().stream()
                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), ignoredTx.getDestination()))
                .collect(toSet());
        Set<UUID> ignoredUTXOsID = ignoredUTXOs.stream().map(StorageUTXO::getId).collect(toSet());

        SlimTransaction acceptedTx = new SlimTransaction(otherKeys.getPublic(), myKeys.getPublic(),
                List.of(randomUUID()), List.of(1,2,3,4,5,6,7,8), emptyList(), otherKeys.getPrivate());
        Set<StorageUTXO> acceptedUTXOs = acceptedTx.getOutputsDestination().stream()
                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), acceptedTx.getDestination()))
                .collect(toSet());
        Set<UUID> acceptedUTXOsID = acceptedUTXOs.stream().map(StorageUTXO::getId).collect(toSet());

        tg.updateUtxos(emptySet(), concat(ignoredUTXOs.stream(), acceptedUTXOs.stream()).collect(toSet()));
        assertTrue(tg.myUTXOs.keySet().stream().allMatch(u -> !ignoredUTXOsID.contains(u) && acceptedUTXOsID.contains(u)));
    }

    /**
     * Generates a low amount transaction from the original special UTXO.
     * Verifies that the UTXO is used, and the resulting transaction has two UTXOs
     * 1 -> Utxo destined to the transaction destination holding the amount of the transaction specified.
     * 2 -> Utxo to the issuer with the difference in the amounts of the transaction and the UTXO sent.
     */
    @Test
    void testInitialGeneratedTransaction() throws Exception {
        tg.myUTXOs.put(randomUUID(), 10);
        int amount = 2;
        int initialBalance = tg.countMyCoins();
        SlimTransaction tx = tg.generateTransaction(otherKeys.getPublic(), amount);
        assertEquals(1, tx.getInputs().size());
        assertEquals(1, tx.getOutputsDestination().size());
        assertEquals(amount, tx.getOutputsDestination().get(0).getAmount());
        assertEquals(1, tx.getOutputsOrigin().size());
        int currentBalance = tg.countMyCoins();
        assertEquals(initialBalance - currentBalance - amount,
                tx.getOutputsOrigin().get(0).getAmount());
    }


    @Test
    void testUserSendTwoTransactions() throws IOException, SignatureException, InvalidKeyException {
        try {
            tg.myUTXOs.put(randomUUID(), 9999);
            int amount = 1;
            tg.generateTransaction(otherKeys.getPublic(), amount);
            tg.generateTransaction(otherKeys.getPublic(), amount);
            fail();
        } catch (NotEnoughCoinsException ignored) {}
    }

    /**
     * Puts several low valued inputs into the {@link TransactionGenerator} and issues a transaction.
     * Verifies that all inputs are consumed and the only transaction output contains the correct amount.
     */
    @Test
    void testSeveralInputsTransactionNoChange() throws Exception {
        int cost = 100;
        int utxoAmount = 10;
        assertTrue(tg.myUTXOs.isEmpty());
        Set<UUID> inputs = new HashSet<>(2 * (cost / utxoAmount));
        for(int i = 0; i < 2 * (cost / utxoAmount); i++) {
            UUID id = randomUUID();
            inputs.add(id);
            tg.myUTXOs.put(id, utxoAmount);
        }
        SlimTransaction tx = tg.generateTransaction(otherKeys.getPublic(), cost);
        assertEquals(cost / utxoAmount, tx.getInputs().size());
        assertTrue(inputs.containsAll(tx.getInputs()));
        assertTrue(inputs.containsAll(tg.myUTXOs.keySet()));
        assertEquals(cost, tx.getOutputsDestination().get(0).getAmount());
        assertTrue(tx.getOutputsOrigin().isEmpty());
    }

    /**
     * Puts several low valued inputs into the {@link TransactionGenerator} and issues a transaction.
     * The cost issued for the transaction is not divisible with the amounts on the created inputs.
     * Verifies that all inputs are consumed and the only two transaction outputs are created.
     * One output containing the transaction amount issued and another with the expected change.
     */
    @Test
    void testSeveralInputsWithChange() throws Exception {
        int cost = 100;
        int utxoAmount = 10;
        assertTrue(tg.myUTXOs.isEmpty());
        Set<UUID> inputs = new HashSet<>(2 * (cost / utxoAmount));
        for(int i = 0; i < 2 * (cost / utxoAmount); i++) {
            UUID id = randomUUID();
            inputs.add(id);
            tg.myUTXOs.put(id, utxoAmount);
        }
        SlimTransaction tx = tg.generateTransaction(otherKeys.getPublic(), cost - 1);
        assertEquals(cost / utxoAmount, tx.getInputs().size());
        assertTrue(inputs.containsAll(tx.getInputs()));
        assertTrue(inputs.containsAll(tg.myUTXOs.keySet()));
        assertEquals(cost - 1, tx.getOutputsDestination().get(0).getAmount());
        assertFalse(tx.getOutputsOrigin().isEmpty());
        assertEquals(1, tx.getOutputsOrigin().get(0).getAmount());
    }

    /**
     * Attempts to create a transaction when the inputs in the {@link TransactionGenerator} do not cover the costs.
     * In such scenarios the {@link NotEnoughCoinsException} should be triggered.
     */
    @Test
    void testNotEnoughInputsTransaction() throws Exception {
        try {
            tryToTestNotEnoughInputsTransaction();
            fail();
        } catch (NotEnoughCoinsException ignored) {}
    }

    private void tryToTestNotEnoughInputsTransaction() throws Exception {
        int cost = 100;
        int utxoAmount = 10;
        assertTrue(tg.myUTXOs.isEmpty());
        for(int i = 0; i < (cost / (2 * utxoAmount)); i++) {
            UUID id = randomUUID();
            tg.myUTXOs.put(id, utxoAmount);
        }
        tg.generateTransaction(otherKeys.getPublic(), cost);
    }

}
