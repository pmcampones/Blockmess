package broadcastProtocols.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RequestRecoveryContentMessage extends ProtoMessage {

    public static final short ID = 1200;

    private final Set<UUID> iHave;

    public RequestRecoveryContentMessage(Set<UUID> iHave) {
        super(ID);
        this.iHave = iHave;
    }

    public Set<UUID> getIHaveIdentifiers() {
        return iHave;
    }

    public static final ISerializer<RequestRecoveryContentMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(RequestRecoveryContentMessage msg, ByteBuf out) {
            out.writeInt(msg.iHave.size());
            for (UUID mid : msg.iHave) {
                out.writeLong(mid.getMostSignificantBits());
                out.writeLong(mid.getLeastSignificantBits());
            }
        }

        @Override
        public RequestRecoveryContentMessage deserialize(ByteBuf in) {
            int numMsgs = in.readInt();
            Set<UUID> iHave = new HashSet<>(numMsgs);
            for (int i = 0; i < numMsgs; i++) {
                iHave.add(new UUID(in.readLong(), in.readLong()));
            }
            return new RequestRecoveryContentMessage(iHave);
        }
    };
}
