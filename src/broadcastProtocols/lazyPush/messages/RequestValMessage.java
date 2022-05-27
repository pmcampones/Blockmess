package broadcastProtocols.lazyPush.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

public class RequestValMessage extends ProtoMessage {

    public static final short ID = 201;
    public static final ISerializer<RequestValMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(RequestValMessage message, ByteBuf out) {
            out.writeLong(message.missing.getMostSignificantBits());
            out.writeLong(message.missing.getLeastSignificantBits());
        }

        @Override
        public RequestValMessage deserialize(ByteBuf in) {
            UUID missing = new UUID(in.readLong(), in.readLong());
            return new RequestValMessage(missing);
        }

    };
    private final UUID missing;

    public RequestValMessage(UUID missing) {
        super(ID);
        this.missing = missing;
    }

    public UUID getMissingMessageId() {
        return missing;
    }

    @Override
    public String toString() {
        return "RequestValMessage{" +
                "missing=" + missing +
                '}';
    }

}
