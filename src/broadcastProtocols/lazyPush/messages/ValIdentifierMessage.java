package broadcastProtocols.lazyPush.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

public class ValIdentifierMessage extends ProtoMessage {

    public static final short ID = 202;

    private final UUID id;

    public ValIdentifierMessage(UUID id) {
        super(ID);
        this.id = id;
    }

    public UUID getIdOfTheContentMessage() {
        return id;
    }

    @Override
    public String toString() {
        return "ValIdentifierMessage{" +
                "mid=" + id +
                '}';
    }

    public static ISerializer<ValIdentifierMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ValIdentifierMessage m, ByteBuf out) {
            out.writeLong(m.id.getMostSignificantBits());
            out.writeLong(m.id.getLeastSignificantBits());
        }

        @Override
        public ValIdentifierMessage deserialize(ByteBuf in) {
            UUID mid = new UUID(in.readLong(), in.readLong());
            return new ValIdentifierMessage(mid);
        }
    };
}
