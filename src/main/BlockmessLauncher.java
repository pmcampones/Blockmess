package main;

import blockConstructors.ContentStorage;
import blockConstructors.StructuredValuesTxLoader;
import broadcastProtocols.BroadcastValue;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.txs.StructuredValueSlimTransactionWrapper;
import catecoin.txs.Transaction;
import ledger.AppContent;
import ledger.BabelLedger;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.ledgerManager.LedgerManager;
import mempoolManager.MempoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import peerSamplingProtocols.hyparview.HyparView;
import peerSamplingProtocols.hyparview.channels.MultiLoggerChannelInitializer;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.network.data.Host;
import sybilResistantElection.SybilResistantElection;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;
import utils.InterfaceToIp;
import valueDispatcher.ValueDispatcher;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

public class BlockmessLauncher {

    public static long startTime;

    //Sets the log4j (logging library) configuration file
    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    }

    //Creates the logger object
    private static final Logger logger = LogManager.getLogger(BlockmessLauncher.class);

    //Default babel configuration file (can be overridden by the "-config" launch argument)
    public static final String DEFAULT_CONF = "config/config.properties";

    public static void main(String[] args) throws Exception {
        startTime = System.currentTimeMillis();
        Babel babel = Babel.getInstance();
        Properties props = initializeProperties(args);
        GlobalProperties.setProps(props);
        babel.registerChannelInitializer("SharedTCP", new MultiLoggerChannelInitializer());
        int port = getNodePort(props);
        Host myself = new Host(InetAddress.getByName(props.getProperty("address")), port);
        logger.info("Hello, I am {} and my contact is {}.", myself, props.getProperty("contact"));
        launchBlockmess(props, myself, babel);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));
    }

    private static Properties initializeProperties(String[] args) throws Exception {
        Properties props = Babel.loadConfig(args, DEFAULT_CONF);
        redirectOutput(props);
        InterfaceToIp.addInterfaceIp(props);
        return props;
    }

    private static void redirectOutput(Properties props) {
        String redirectionFile = props.getProperty("redirectFile");
        System.setProperty("logFileName", redirectionFile);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        ctx.reconfigure();
    }

    private static int getNodePort(Properties props) {
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

    private static void launchBlockmess(Properties props, Host myself, Babel babel) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>(addNetworkProtocols(myself));
        MempoolManager mempoolManager = MempoolManager.getSingleton();
        protocols.add(mempoolManager);
        LedgerManager ledgerManager = setUpLedgerManager(protocols);
        bootstrapContent(props, ledgerManager);
        setUpSybilElection(protocols);
        initializeSerializers();
        initializeProtocols(props, babel, protocols);
    }

    private static void setUpSybilElection(List<GenericProtocol> protocols) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys();
        protocols.add(new SybilResistantElection(myKeys));
    }

    @NotNull
    private static LedgerManager setUpLedgerManager(List<GenericProtocol> protocols)
            throws HandlerRegistrationException {
        LedgerManager ledgerManager = LedgerManager.getSingleton();
        var babelLedger = new BabelLedger(ledgerManager);
        protocols.add(babelLedger);
        return ledgerManager;
    }

    private static void bootstrapContent(Properties props, LedgerManager ledgerManager) {
        var txsLoader = new StructuredValuesTxLoader(ledgerManager);
        if (props.getProperty("allowCommonTransactionsAmongChains", "F").equals("F")) {
            loadTxsForBlockmess(txsLoader);
            assert(areAllTxsDistinctAmongChains());
        } else {
            loadTxsCommon();
        }
    }

    private static boolean areAllTxsDistinctAmongChains() {
        List<UUID> allTxsIds = LedgerManager.getSingleton().getAvailableChains().stream()
                .map(ContentStorage::getStoredContent)
                .flatMap(Collection::stream)
                .map(AppContent::getId)
                .collect(Collectors.toList());
        return allTxsIds.size() == new HashSet<>(allTxsIds).size();
    }

    private static void loadTxsCommon() {
        Properties props = GlobalProperties.getProps();
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator().generateFakeTxs(numTxs);
        var structuredValues = txs.stream()
                .map(StructuredValueSlimTransactionWrapper::wrapTx)
                .collect(Collectors.toList());
        LedgerManager.getSingleton().getAvailableChains().forEach(b -> b.submitContentDirectly(structuredValues));
    }

    private static void loadTxsForBlockmess(StructuredValuesTxLoader txsLoader) {
        Properties props = GlobalProperties.getProps();
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator().generateFakeTxs(numTxs);
        var structuredValues = txs.stream()
                .map(StructuredValueSlimTransactionWrapper::wrapTx)
                .collect(Collectors.toList());
        txsLoader.loadTxs(structuredValues);
    }

    private static List<GenericProtocol> addNetworkProtocols(Host myself) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>();
        HyparView peerSamplingProtocol = new HyparView(myself);;
        protocols.add(peerSamplingProtocol);
        protocols.addAll(addBroadcastProtocols(myself, peerSamplingProtocol));
        protocols.add(ValueDispatcher.getSingleton());
        return protocols;
    }

    private static void initializeSerializers() {
        BroadcastValue.pojoSerializers.put(BlockmessBlock.ID, BlockmessBlock.serializer);
        BroadcastValue.pojoSerializers.put(ContentList.ID, ContentList.serializer);
        BroadcastValue.pojoSerializers.put(Transaction.ID, Transaction.serializer);
        BroadcastValue.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
        BroadcastValue.pojoSerializers.put(AppContent.ID, AppContent.serializer);
    }

    private static List<GenericProtocol> addBroadcastProtocols(Host myself, HyparView peerSamplingProtocol)
            throws HandlerRegistrationException, IOException {
        List<GenericProtocol> protocols = new LinkedList<>();
        protocols.add(new LazyPushBroadcast(myself));
        protocols.add(new EagerPushBroadcast(peerSamplingProtocol));
        return protocols;
    }

    private static void initializeProtocols(Properties props, Babel babel, List<GenericProtocol> protocols)
            throws ProtocolAlreadyExistsException, HandlerRegistrationException, IOException {
        for (GenericProtocol protocol : protocols)
            babel.registerProtocol(protocol);
        babel.start();
        for (GenericProtocol protocol : protocols) {
            protocol.init(props);
        }
    }

}
