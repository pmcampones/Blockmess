package peerSamplingProtocols.hyparview.channels;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.channels.multi.MultiChannel;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedBiChannel;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MultiLoggerChannel extends SingleThreadedBiChannel<BabelMessage, BabelMessage> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(MultiLoggerChannel.class);

    private static MultiLoggerChannel multiChannelInstance = null;
    private static MultiChannel multiChannel;

    private final Map<Connection<BabelMessage>, Long> bytes;

    private String msgName(BabelMessage msg) {
        return msg.getClass().getSimpleName();
    }

    public static MultiLoggerChannel getInstance(ISerializer<BabelMessage> serializer,
                                                 ChannelListener<BabelMessage> list,
                                                 short protoId,
                                                 Properties properties)  throws IOException {
        if(multiChannelInstance == null)
            multiChannelInstance = new MultiLoggerChannel();

        multiChannel = MultiChannel.getInstance(serializer, list, protoId, properties);
        return multiChannelInstance;
    }


    private MultiLoggerChannel() {
        super("MultiLoggerChannel");
        bytes = new HashMap<>();
    }

    @Override
    protected void onInboundConnectionUp(Connection<BabelMessage> con) {
        multiChannel.inboundConnectionUp(con);
    }

    @Override
    protected void onInboundConnectionDown(Connection<BabelMessage> con, Throwable cause) {
        multiChannel.inboundConnectionDown(con, cause);
        bytes.remove(con);
    }

    @Override
    protected void onServerSocketBind(boolean success, Throwable cause) {
        if (!success)
            logger.error("Server socket bind failed: " + cause);
    }

    @Override
    protected void onServerSocketClose(boolean success, Throwable cause) {
        multiChannel.serverSocketClose(success, cause);
    }

    @Override
    protected void onOutboundConnectionUp(Connection<BabelMessage> conn) {
        multiChannel.outboundConnectionUp(conn);
    }

    @Override
    protected void onOutboundConnectionDown(Connection<BabelMessage> conn, Throwable cause) {
        multiChannel.outboundConnectionDown(conn, cause);
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<BabelMessage> conn, Throwable cause) {
        multiChannel.outboundConnectionFailed(conn, cause);
    }

    @Override
    protected void onSendMessage(BabelMessage msg, Host peer, int connection) {
        if(!msgName(msg).equals("IHaveMessage"))
            logger.debug("Sending msg {} for Proto {} to {}", msgName(msg), Translate.ProtoIdToName(msg.getSourceProto()), peer);
        multiChannel.sendMessage(msg, peer, connection);
    }

    @Override
    protected void onCloseConnection(Host peer, int connection) {
        multiChannel.closeConnection(peer, connection);
    }

    @Override
    protected void onDeliverMessage(BabelMessage msg, Connection<BabelMessage> conn) {
        if(!msgName(msg).equals("IHaveMessage"))
            logger.debug("Received msg {} for Proto {} from {} bytes {}", msgName(msg), Translate.ProtoIdToName(msg.getSourceProto()), conn.getPeer(),
                    conn.getReceivedAppBytes() - bytes.getOrDefault(conn, 0L));
        bytes.put(conn, conn.getReceivedAppBytes());
        multiChannel.deliverMessage(msg, conn);
    }

    @Override
    protected void onOpenConnection(Host peer) {
        multiChannel.openConnection(peer);
    }

    @Override
    public boolean validateAttributes(Attributes attr) {
        return multiChannel.validateAttributes(attr);
    }
}