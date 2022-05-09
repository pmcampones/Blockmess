package valueDispatcher;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class DispatcherWrapper {

    public static final short ID = 1000;

    private final short dispatcherType;

    public static final ISerializer<DispatcherWrapper> serializer = new ISerializer<>() {

        @Override
        public void serialize(DispatcherWrapper wrapper, ByteBuf out) throws IOException {
            out.writeShort(wrapper.dispatcherType);
            out.writeShort(wrapper.val.getClassId());
            wrapper.val.getSerializer().serialize(wrapper.val, out);
        }

        @Override
        public DispatcherWrapper deserialize(ByteBuf in) throws IOException {
            return new DispatcherWrapper(in.readShort(),
                    BroadcastValue.pojoSerializers.get(in.readShort()).deserialize(in));
        }
    };
    private final BroadcastValue val;

    public short getDispatcherType() {
        return dispatcherType;
    }

    public DispatcherWrapper(short dispatcherType, BroadcastValue val) {
        this.dispatcherType = dispatcherType;
        this.val = val;
    }

    public boolean isBlocking() {
        return val.isBlocking();
    }

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

    public ISerializer<DispatcherWrapper> getSerializer() {
        return serializer;
    }

}
