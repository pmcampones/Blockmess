package applicationInterface.launcher;

import applicationInterface.GlobalProperties;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import peerSamplingProtocols.hyparview.HyparView;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.InterfaceToIp;
import valueDispatcher.ValueDispatcher;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static java.lang.Integer.parseInt;

public class LauncherCommon {

    //Default babel configuration file (can be overridden by the "-config" launch argument)
    public static final String DEFAULT_CONF = "config/config.properties";

    public static Properties initializeProperties(String[] args) throws Exception {
        Properties props = Babel.loadConfig(args, DEFAULT_CONF);
        InterfaceToIp.addInterfaceIp(props);
        return props;
    }

    public static int getNodePort(Properties props) {
        int port = parseInt(props.getProperty("port"));
        while (!isPortAvailable(port))
            port++;
        return port;
    }

    // Shamelessly adapted from:
    // https://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
    public static boolean isPortAvailable(int port) {
        try {
            ServerSocket ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            DatagramSocket ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            try {
                ds.close();
                ss.close();
            } catch (IOException ignored) {
                /* should not be thrown */
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void initializeProtocols(Babel babel, List<GenericProtocol> protocols)
            throws ProtocolAlreadyExistsException, HandlerRegistrationException, IOException {
        for (GenericProtocol protocol : protocols)
            babel.registerProtocol(protocol);
        babel.start();
        Properties props = GlobalProperties.getProps();
        for (GenericProtocol protocol : protocols) {
            protocol.init(props);
        }
    }

    public static void redirectOutput() {
        String redirectionFile = GlobalProperties.getProps().getProperty("redirectFile");
        System.setProperty("logFileName", redirectionFile);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        ctx.reconfigure();
    }

    @SneakyThrows
    public static List<GenericProtocol> addNetworkProtocols(Host myself) {
        List<GenericProtocol> protocols = new LinkedList<>();
        HyparView peerSamplingProtocol = new HyparView(myself);
        protocols.add(peerSamplingProtocol);
        protocols.addAll(addBroadcastProtocols(myself));
        protocols.add(ValueDispatcher.getSingleton());
        return protocols;
    }

    @SneakyThrows
    private static List<GenericProtocol> addBroadcastProtocols(Host myself) {
        List<GenericProtocol> protocols = new LinkedList<>();
        protocols.add(new LazyPushBroadcast(myself));
        protocols.add(new EagerPushBroadcast());
        return protocols;
    }

}
