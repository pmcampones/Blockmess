package peerSamplingProtocols.hyparview.channels;

import pt.unl.fct.di.novasys.babel.initializers.ChannelInitializer;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Properties;

public class MultiLoggerChannelInitializer implements ChannelInitializer<MultiLoggerChannel> {
    @Override
    public MultiLoggerChannel initialize(ISerializer<BabelMessage> serializer, ChannelListener<BabelMessage> list, Properties properties, short protoId) throws IOException {
        return MultiLoggerChannel.getInstance(serializer, list, protoId, properties);
    }

}