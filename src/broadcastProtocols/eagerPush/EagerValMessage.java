package broadcastProtocols.eagerPush;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import broadcastProtocols.messages.BatcheableMessage;
import broadcastProtocols.messages.BroadcastMessage;
import broadcastProtocols.messages.BroadcastMessageImp;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import valueDispatcher.DispatcherWrapper;

import java.io.IOException;
import java.util.UUID;

public class EagerValMessage extends BatcheableMessage implements BroadcastMessage {

    public static final short ID = 786;

    private final BroadcastMessage broadcastMessage;

    public EagerValMessage(UUID mid, DispatcherWrapper val) {
        super(ID);
        this.broadcastMessage = new BroadcastMessageImp(mid, val);
    }

    private EagerValMessage(BroadcastMessage broadcastMessage) {
        super(ID);
        this.broadcastMessage = broadcastMessage;
    }

    @Override
    public UUID getMid() {
        return broadcastMessage.getMid();
    }

    @Override
    public DispatcherWrapper getVal() {
        return broadcastMessage.getVal();
    }

    @Override
    public boolean isBlocking() {
        return broadcastMessage.isBlocking();
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return broadcastMessage.getBlockingID();
    }

    public static final ISerializer<BatcheableMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(BatcheableMessage msg, ByteBuf out) throws IOException {
            BroadcastMessageImp.serializer.serialize(msg, out);
        }

        @Override
        public EagerValMessage deserialize(ByteBuf in) throws IOException {
            BroadcastMessage msg = (BroadcastMessage) BroadcastMessageImp.serializer.deserialize(in);
            return new EagerValMessage(msg);
        }

    };

    @Override
    public ISerializer<BatcheableMessage> getSerializer() {
        return serializer;
    }
}
