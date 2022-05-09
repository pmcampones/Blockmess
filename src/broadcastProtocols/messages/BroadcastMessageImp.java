package broadcastProtocols.messages;

import broadcastProtocols.eagerPush.EagerValMessage;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import valueDispatcher.DispatcherWrapper;

import java.io.IOException;
import java.util.UUID;

public class BroadcastMessageImp extends BatcheableMessage implements BroadcastMessage {

    private final UUID mid;

    public static final ISerializer<BatcheableMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(BatcheableMessage msg, ByteBuf out) throws IOException {
            BroadcastMessage broadcastMessage = (BroadcastMessage) msg;
            out.writeLong(broadcastMessage.getMid().getMostSignificantBits());
            out.writeLong(broadcastMessage.getMid().getLeastSignificantBits());
            DispatcherWrapper.serializer.serialize(broadcastMessage.getVal(), out);
        }

        @Override
        public BatcheableMessage deserialize(ByteBuf in) throws IOException {
            UUID mid = new UUID(in.readLong(), in.readLong());
            DispatcherWrapper val = DispatcherWrapper.serializer.deserialize(in);
            return new EagerValMessage(mid, val);
        }

    };
    private final DispatcherWrapper val;

    @Override
    public UUID getMid() {
        return mid;
    }

    public BroadcastMessageImp(UUID mid, DispatcherWrapper val) {
        super((short) -1);  //Don't instantiate to be used inside Babel.
        // This is only here because I can't turn ProtoMessage into an interface
        this.mid = mid;
        this.val = val;
    }

    @Override
    public boolean isBlocking() {
        return val.isBlocking();
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return val.getBlockingID();
    }

    @Override
    public DispatcherWrapper getVal() {
        return val;
    }

    @Override
    public ISerializer<BatcheableMessage> getSerializer() {
        return serializer;
    }

}
