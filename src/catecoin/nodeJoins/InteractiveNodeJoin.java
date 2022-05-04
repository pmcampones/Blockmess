package catecoin.nodeJoins;

import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.PublicKey;

public class InteractiveNodeJoin extends ProtoPojoAbstract {

    public static final short ID = 1101;

    private final PublicKey node;

    private final String username;


    public InteractiveNodeJoin(PublicKey node, String username) {
        super(ID);
        this.node = node;
        this.username = username;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            InteractiveNodeJoin interactiveNodeJoin = (InteractiveNodeJoin) pojo;
            CryptographicUtils.serializeKey(interactiveNodeJoin.node, out);
            byte[] usernameBytes = interactiveNodeJoin.username.getBytes();
            out.writeShort(usernameBytes.length);
            out.writeBytes(usernameBytes);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            PublicKey node = CryptographicUtils.deserializePubKey(in);
            byte[] usernameBytes = new byte[in.readShort()];
            in.readBytes(usernameBytes);
            return new InteractiveNodeJoin(node, new String(usernameBytes));
        }
    };
}
