package valueDispatcher;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class DispatcherWrapper extends BroadcastValueAbstract {

    public static final short ID = 1000;

    private final short dispatcherType;

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue pojo, ByteBuf out) throws IOException {
            DispatcherWrapper wrapper = (DispatcherWrapper) pojo;
            out.writeShort(wrapper.dispatcherType);
            out.writeShort(wrapper.val.getClassId());
            wrapper.val.getSerializer().serialize(wrapper.val, out);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) throws IOException {
            return new DispatcherWrapper(in.readShort(),
                    BroadcastValue.pojoSerializers.get(in.readShort()).deserialize(in));
        }
    };
    private final BroadcastValue val;

    public short getDispatcherType() {
        return dispatcherType;
    }

    public DispatcherWrapper(short dispatcherType, BroadcastValue val) {
        super(ID);
        this.dispatcherType = dispatcherType;
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
    public String toString() {
        return String.format("Wrapper of [%s]", val);
    }

    public BroadcastValue getVal() {
        return val;
    }

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }

}
