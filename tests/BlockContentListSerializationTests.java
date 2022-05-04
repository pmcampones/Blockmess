package tests;

import catecoin.blocks.BlockContentList;
import catecoin.txs.SlimTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.blocks.BlockContent;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockContentListSerializationTests {

    public BlockContentListSerializationTests() {
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
    }

    @Test
    void shouldSerializeEmptyCatecoinBlockContent() throws IOException {
        BlockContent<SlimTransaction> og = new BlockContentList<>(Collections.emptyList());
        ByteBuf byteBuf = Unpooled.buffer();
        BlockContentList.serializer.serialize(og, byteBuf);
        BlockContent<SlimTransaction> cpy = (BlockContent<SlimTransaction>) BlockContentList.serializer.deserialize(byteBuf);
        assertTrue(cpy.getContentList().isEmpty());
    }

    @Test
    void shouldSerializeCatecoinBlockContentWithASingleTx()
            throws IOException, InvalidAlgorithmParameterException, NoSuchAlgorithmException,
            SignatureException, InvalidKeyException {
        KeyPair sender = CryptographicUtils.generateECDSAKeyPair();
        PublicKey receiver = CryptographicUtils.generateECDSAKeyPair().getPublic();
        SlimTransaction tx = new SlimTransaction(sender.getPublic(), receiver,
                List.of(UUID.randomUUID()), List.of(2), List.of(3), sender.getPrivate());
        BlockContent<SlimTransaction> og = new BlockContentList<>(List.of(tx));
        ByteBuf byteBuf = Unpooled.buffer();
        BlockContentList.serializer.serialize(og, byteBuf);
        BlockContent<SlimTransaction> cpy = (BlockContent<SlimTransaction>) BlockContentList.serializer.deserialize(byteBuf);
        assertEquals(tx.getId(), cpy.getContentList().get(0).getId());
    }

}
