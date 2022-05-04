package valueDispatcher.pojos;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class DispatcherWrapper extends ProtoPojoAbstract {

    public static final short ID = 1000;

    private final short dispatcherType;

    private final ProtoPojo val;

    public DispatcherWrapper(short dispatcherType, ProtoPojo val) {
        super(ID);
        this.dispatcherType = dispatcherType;
        this.val = val;
    }

    public short getDispatcherType() {
        return dispatcherType;
    }

    public ProtoPojo getVal() {
        return val;
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

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            DispatcherWrapper wrapper = (DispatcherWrapper) pojo;
            out.writeShort(wrapper.dispatcherType);
            out.writeShort(wrapper.val.getClassId());
            wrapper.val.getSerializer().serialize(wrapper.val, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            return new DispatcherWrapper(in.readShort(),
                    ProtoPojo.pojoSerializers.get(in.readShort()).deserialize(in));
        }
    };

}
