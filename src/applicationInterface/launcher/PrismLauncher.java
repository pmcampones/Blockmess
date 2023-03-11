package applicationInterface.launcher;

import applicationInterface.ApplicationInterface;
import applicationInterface.GlobalProperties;
import broadcastProtocols.BroadcastValue;
import cmux.AppOperation;
import ledger.blocks.ContentList;
import mempoolManager.MempoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.hyparview.channels.MultiLoggerChannelInitializer;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class PrismLauncher {

    //Creates the logger object
    private static final Logger logger = LogManager.getLogger(PrismLauncher.class);
    public static long startTime;

    //Sets the log4j (logging library) configuration file
    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    }

    public static void launchPrism(String[] args, ApplicationInterface protocol) {
        try {
            tryToLaunchPrism(args, protocol);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void tryToLaunchPrism(String[] args, ApplicationInterface protocol) throws Exception {
        startTime = System.currentTimeMillis();
        Babel babel = Babel.getInstance();
        Properties props = LauncherCommon.initializeProperties(args);
        GlobalProperties.setProps(props);
        LauncherCommon.redirectOutput();
        babel.registerChannelInitializer("SharedTCP", new MultiLoggerChannelInitializer());
        int port = LauncherCommon.getNodePort(props);
        Host myself = new Host(InetAddress.getByName(props.getProperty("address")), port);
        logger.info("Hello, I am {} and my contact is {}.", myself, props.getProperty("contact"));
        launchPrism(myself, babel, List.of(protocol));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));
    }

    private static void launchPrism(Host myself, Babel babel, Collection<GenericProtocol> appProtocols) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>(LauncherCommon.addNetworkProtocols(myself));
        protocols.add(MempoolManager.getSingleton());
        initializeSerializers();
        protocols.addAll(appProtocols);
        LauncherCommon.initializeProtocols(babel, protocols);
    }

    private static void initializeSerializers() {
        //TODO PrismBlock and PrismSybilProof should be registered here
        BroadcastValue.pojoSerializers.put(ContentList.ID, ContentList.serializer);
        BroadcastValue.pojoSerializers.put(AppOperation.ID, AppOperation.serializer);
    }

}
