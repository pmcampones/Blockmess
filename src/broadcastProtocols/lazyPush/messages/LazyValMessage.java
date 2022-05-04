package broadcastProtocols.lazyPush.messages;

import broadcastProtocols.messages.BroadcastMessage;
import broadcastProtocols.messages.BroadcastMessageImp;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import broadcastProtocols.messages.BatcheableMessage;
import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class LazyValMessage extends BatcheableMessage implements BroadcastMessage {

    public static final short ID = 200;

    private final BroadcastMessage broadcastMessage;

    public LazyValMessage(UUID mid, ProtoPojo val) {
        super(ID);
        this.broadcastMessage = new BroadcastMessageImp(mid, val);
    }

    private LazyValMessage(BroadcastMessage broadcastMessage) {
        super(ID);
        this.broadcastMessage = broadcastMessage;
    }

    @Override
    public UUID getMid() {
        return broadcastMessage.getMid();
    }

    @Override
    public ProtoPojo getVal()  {
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

    @Override
    public String toString() {
        return String.format("Lazy message %s with content [%s]",
                broadcastMessage.getMid(), broadcastMessage.getVal());
    }

    public static final ISerializer<BatcheableMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(BatcheableMessage msg, ByteBuf out) throws IOException {
            BroadcastMessageImp.serializer.serialize(msg, out);
        }

        @Override
        public LazyValMessage deserialize(ByteBuf in) throws IOException {
            BroadcastMessage msg = (BroadcastMessage) BroadcastMessageImp.serializer.deserialize(in);
            return new LazyValMessage(msg);
        }
    };

    @Override
    public ISerializer<BatcheableMessage> getSerializer() {
        return serializer;
    }
}
