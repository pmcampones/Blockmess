package catecoin.nodeJoins;

import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.PublicKey;

public class AutomatedNodeJoin extends ProtoPojoAbstract {

    public static final short ID = 1100;

    private final PublicKey key;


    public AutomatedNodeJoin(PublicKey key) {
        super(ID);
        this.key = key;
    }

    public PublicKey getKey() {
        return key;
    }

    @Override
    public String toString() {

        return String.format("AutomatedNodeJoin: %s", "None");
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            AutomatedNodeJoin nodeJoin = (AutomatedNodeJoin) pojo;
            CryptographicUtils.serializeKey(nodeJoin.key, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            PublicKey key = CryptographicUtils.deserializePubKey(in);
            return new AutomatedNodeJoin(key);
        }
    };
}
