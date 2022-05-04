package chatApp;

import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.nio.charset.StandardCharsets;

public class ChatMessage extends ProtoPojoAbstract {

    public static final short ID = 1300;

    private final String message;

    public ChatMessage(String message) {
        super(ID);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) {
            ChatMessage chatMessage = (ChatMessage) pojo;
            byte[] messageBytes = chatMessage.message.getBytes(StandardCharsets.UTF_8);
            out.writeInt(messageBytes.length);
            out.writeBytes(messageBytes);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) {
            byte[] messageBytes = new byte[in.readInt()];
            in.readBytes(messageBytes);
            return new ChatMessage(new String(messageBytes, StandardCharsets.UTF_8));
        }
    };
}
