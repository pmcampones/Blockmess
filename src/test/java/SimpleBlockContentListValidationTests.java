package test.java;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import main.CryptographicUtils;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleBlockContentListValidationTests {

    @Test
    void shouldValidateEmptyContent() {
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(emptyList());
        assertTrue(blockContent.hasValidSemantics());
    }

    @Test
    void shouldValidateFullContent() throws Exception {
        BlockContent<SlimTransaction> blockContent = new SimpleBlockContentList<>(getTxs());
        assertTrue(blockContent.hasValidSemantics());
    }

    private static List<SlimTransaction> getTxs() throws Exception {
        int numTxs = 20;
        List<SlimTransaction> txs = new ArrayList<>(numTxs);
        for (int i = 0; i < numTxs; i++)
            txs.add(getTx());
        return txs;
    }

    private static SlimTransaction getTx() throws Exception {
        KeyPair proposer = CryptographicUtils.generateECDSAKeyPair();
        PublicKey receiver = CryptographicUtils.generateECDSAKeyPair().getPublic();
        List<UUID> inputs = List.of(
                randomUUID(),
                randomUUID(),
                randomUUID()
        );
        List<Integer> outputsDestination = genRandomInts();
        List<Integer> outputsOrigin = genRandomInts();
        return new SlimTransaction(proposer.getPublic(), receiver, inputs,
                outputsDestination, outputsOrigin, proposer.getPrivate());
    }

    private static List<Integer> genRandomInts() {
        int numInts = 5;
        Random r = new Random();
        return IntStream.range(0, numInts)
                .mapToObj(i -> r.nextInt(100) + 1)
                .collect(toList());
    }

}
