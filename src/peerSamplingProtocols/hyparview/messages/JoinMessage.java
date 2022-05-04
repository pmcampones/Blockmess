package peerSamplingProtocols.hyparview.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class JoinMessage extends ProtoMessage {
    public final static short MSG_CODE = 3401;


    public JoinMessage() {
        super(JoinMessage.MSG_CODE);
    }

    @Override
    public String toString() {
        return "JoinMessage{}";
    }

    public static final ISerializer<JoinMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(JoinMessage m, ByteBuf out) {}

        @Override
        public JoinMessage deserialize(ByteBuf in) {
            return new JoinMessage();
        }
    };
}
