package broadcastProtocols.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReplyRecoveryContentMessage extends ProtoMessage {

    public static final short ID = 1201;

    private final Set<BatcheableMessage> recoveryContent;

    public ReplyRecoveryContentMessage(Set<BatcheableMessage> recoveryContent) {
        super(ID);
        this.recoveryContent = recoveryContent;
    }

    public Set<BatcheableMessage> getRecoveryContent() {
        return recoveryContent;
    }

    public static final ISerializer<ReplyRecoveryContentMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(ReplyRecoveryContentMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.recoveryContent.size());
            for (BatcheableMessage inner : msg.recoveryContent) {
                out.writeShort(inner.getId());
                inner.getSerializer().serialize(inner, out);
            }
        }

        @Override
        public ReplyRecoveryContentMessage deserialize(ByteBuf in) throws IOException {
            int numMsgs = in.readInt();
            Set<BatcheableMessage> recoveryContent = new HashSet<>(numMsgs);
            for (int i = 0; i < numMsgs; i++) {
                short mid = in.readShort();
                recoveryContent.add(BatcheableMessage.serializers.get(mid).deserialize(in));
            }
            return new ReplyRecoveryContentMessage(Collections.unmodifiableSet(recoveryContent));
        }
    };

}
