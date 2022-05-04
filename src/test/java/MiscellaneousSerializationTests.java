package test.java;

import catecoin.nodeJoins.AutomatedNodeJoin;
import catecoin.nodeJoins.InteractiveNodeJoin;
import chatApp.ChatMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import main.CryptographicUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MiscellaneousSerializationTests {

    @Test
    void testSerializeChatMessage() throws IOException {
        String message = "The quick brown fox jumped over the lazy dog.";
        ChatMessage chatMessageOg = new ChatMessage(message);
        ByteBuf byteBuf = Unpooled.buffer();
        ChatMessage.serializer.serialize(chatMessageOg, byteBuf);
        ChatMessage chatMessageCpy = (ChatMessage) ChatMessage.serializer.deserialize(byteBuf);
        assertEquals(chatMessageOg.getMessage(), chatMessageCpy.getMessage());
    }

    @Test
    void testSerializeAutomatedNodeJoin() throws Exception {
        PublicKey node = CryptographicUtils.generateECDSAKeyPair().getPublic();
        AutomatedNodeJoin nodeJoinOg = new AutomatedNodeJoin(node);
        ByteBuf byteBuf = Unpooled.buffer();
        AutomatedNodeJoin.serializer.serialize(nodeJoinOg, byteBuf);
        AutomatedNodeJoin nodeJoinCpy = (AutomatedNodeJoin) AutomatedNodeJoin.serializer.deserialize(byteBuf);
        assertEquals(nodeJoinOg.getKey(), nodeJoinCpy.getKey());
    }

    @Test
    void testSerializeInteractiveNodeJoin() throws Exception {
        PublicKey node = CryptographicUtils.generateECDSAKeyPair().getPublic();
        String username = "Jean Pierre Polnareff";
        InteractiveNodeJoin nodeJoinOg = new InteractiveNodeJoin(node, username);
        ByteBuf byteBuf = Unpooled.buffer();
        InteractiveNodeJoin.serializer.serialize(nodeJoinOg, byteBuf);
        InteractiveNodeJoin nodeJoinCpy = (InteractiveNodeJoin) InteractiveNodeJoin.serializer.deserialize(byteBuf);
        assertEquals(nodeJoinOg.getNodeKey(), nodeJoinCpy.getNodeKey());
        assertEquals(nodeJoinOg.getUsername(), nodeJoinCpy.getUsername());
    }

}
