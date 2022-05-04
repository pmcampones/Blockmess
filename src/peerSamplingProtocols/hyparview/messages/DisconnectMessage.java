package peerSamplingProtocols.hyparview.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class DisconnectMessage extends ProtoMessage {

    public final static short MSG_CODE = 3404;

    public DisconnectMessage() {
        super(DisconnectMessage.MSG_CODE);
    }

    @Override
    public String toString() {
        return "DisconnectMessage{}";
    }

    public static final ISerializer<DisconnectMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(DisconnectMessage m, ByteBuf out) {}

        @Override
        public DisconnectMessage deserialize(ByteBuf in) {
            return new DisconnectMessage();
        }

    };
}
