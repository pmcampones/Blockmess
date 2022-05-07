package main;

import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import catecoin.blockConstructors.*;
import catecoin.blocks.ContentList;
import catecoin.mempoolManager.BootstrapModule;
import catecoin.mempoolManager.MempoolManager;
import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.txs.StructuredValueSlimTransactionWrapper;
import catecoin.txs.Transaction;
import catecoin.validators.BlockmessGPoETValidator;
import ledger.BabelLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.StructuredValue;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeAlreadyDefinedException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import logsGenerators.ChangesInNumberOfChainsLog;
import logsGenerators.FinalizedBlocksLog;
import logsGenerators.RepeatedTransactionsLog;
import logsGenerators.UnfinalizedBlocksLog;
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

public class Main {

    public static long startTime;

    //Sets the log4j (logging library) configuration file
    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    }

    //Creates the logger object
    private static final Logger logger = LogManager.getLogger(Main.class);

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
        var protocols = new LinkedList<>(addNetworkProtocols(props, myself));
        var mempoolManager = MempoolManager.getSingleton();
        protocols.add(mempoolManager);
        setUpLedgerPrototype(props);
        setUpContentStoragePrototype();
        var ledgerManager = setUpLedgerManager(props, protocols);
        bootstrapContent(props, ledgerManager);
        setUpSybilElection(props, protocols, ledgerManager);
        recordMetricsBlockmess(props, protocols, ledgerManager);
        initializeSerializers();
        initializeProtocols(props, babel, protocols);
    }

    private static void setUpSybilElection(Properties props, List<GenericProtocol> protocols, LedgerManager ledgerManager) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        protocols.add(new SybilResistantElection(props, myKeys, ledgerManager));
    }

    @NotNull
    private static LedgerManager setUpLedgerManager(
            Properties props, List<GenericProtocol> protocols)
            throws PrototypeHasNotBeenDefinedException, HandlerRegistrationException {
        LedgerManager ledgerManager =
                new LedgerManager(props);
        var babelLedger = new BabelLedger<>(ledgerManager);
        protocols.add(babelLedger);
        return ledgerManager;
    }

    private static void setUpContentStoragePrototype() throws PrototypeAlreadyDefinedException {
        PrototypicalContentStorage<StructuredValue<Transaction>> contentStorage = new BaseContentStorage();
        ContentStoragePrototype.setPrototype(contentStorage);
    }

    private static void setUpLedgerPrototype(Properties props) throws PrototypeAlreadyDefinedException {
        var blockValidator = new BlockmessGPoETValidator(props);
        var protoLedger = new Blockchain(props, blockValidator, new BootstrapModule());
        protoLedger.close();
        LedgerPrototype.setPrototype(protoLedger);
    }

    private static void bootstrapContent(Properties props, LedgerManager ledgerManager) {
        var txsLoader = new StructuredValuesTxLoader(ledgerManager);
        if (props.getProperty("allowCommonTransactionsAmongChains", "F").equals("F")) {
            loadTxsForBlockmess(props, txsLoader);
            assert(areAllTxsDistinctAmongChains(ledgerManager));
        } else {
            loadTxsCommon(props, ledgerManager);
        }
    }

    private static boolean areAllTxsDistinctAmongChains(LedgerManager ledgerManager) {
        List<UUID> allTxsIds = ledgerManager.getAvailableChains().stream()
                .map(ContentStorage::getStoredContent)
                .flatMap(Collection::stream)
                .map(StructuredValue::getId)
                .collect(Collectors.toList());
        return allTxsIds.size() == new HashSet<>(allTxsIds).size();
    }

    private static void loadTxsCommon(
            Properties props, LedgerManager ledgerManager) {
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator().generateFakeTxs(numTxs);
        var structuredValues = txs.stream()
                .map(StructuredValueSlimTransactionWrapper::wrapTx)
                .collect(Collectors.toList());
        ledgerManager.getAvailableChains().forEach(b -> b.submitContentDirectly(structuredValues));
    }

    private static void recordMetricsBlockmess(Properties props, List<GenericProtocol> protocols, LedgerManager ledgerManager) throws IOException, HandlerRegistrationException {
        if (props.getProperty("recordUnfinalized", "T").equals("T"))
            ledgerManager.attachObserver(new UnfinalizedBlocksLog(props));
        if (props.getProperty("recordFinalized", "T").equals("T"))
            ledgerManager.attachObserver(new FinalizedBlocksLog(props));
        if (props.getProperty("recordRepeats", "F").equals("T"))
            protocols.add(new RepeatedTransactionsLog(props));
        if (props.getProperty("recordChangesChains", "F").equals("T"))
            ledgerManager.changesLog.add(new ChangesInNumberOfChainsLog(props));
    }

    private static List<GenericProtocol> addNetworkProtocols(Properties props, Host myself) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>();
        HyparView peerSamplingProtocol = new HyparView(props, myself);;
        protocols.add(peerSamplingProtocol);
        protocols.addAll(addBroadcastProtocols(props, myself, peerSamplingProtocol));
        protocols.add(new ValueDispatcher());
        return protocols;
    }

    private static List<GenericProtocol> addBroadcastProtocols(Properties props, Host myself,
                                                               HyparView peerSamplingProtocol)
            throws HandlerRegistrationException, IOException {
        List<GenericProtocol> protocols = new LinkedList<>();
        protocols.add(new LazyPushBroadcast(props, myself));
        protocols.add(new EagerPushBroadcast(peerSamplingProtocol));
        return protocols;
    }

    private static void initializeSerializers() {
        ProtoPojo.pojoSerializers.put(BlockmessBlock.ID, BlockmessBlock.serializer);
        ProtoPojo.pojoSerializers.put(ContentList.ID, ContentList.serializer);
        ProtoPojo.pojoSerializers.put(Transaction.ID, Transaction.serializer);
        ProtoPojo.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
        ProtoPojo.pojoSerializers.put(StructuredValue.ID, StructuredValue.serializer);
    }

    private static void loadTxsForBlockmess(Properties props, StructuredValuesTxLoader txsLoader) {
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator().generateFakeTxs(numTxs);
        var structuredValues = txs.stream()
                .map(StructuredValueSlimTransactionWrapper::wrapTx)
                .collect(Collectors.toList());
        txsLoader.loadTxs(structuredValues);
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
