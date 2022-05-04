package main;

import broadcastProtocols.PeriodicPrunableHashMap;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.eagerPush.EagerValMessage;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import broadcastProtocols.lazyPush.messages.LazyValMessage;
import catecoin.blockConstructors.*;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.mempoolManager.*;
import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.txs.SlimTransaction;
import catecoin.txs.StructuredValueSlimTransactionWrapper;
import catecoin.validators.*;
import com.google.gson.Gson;
import intermediateConsensus.SingleElementCommittee;
import ledger.BabelLedger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.StructuredValue;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeAlreadyDefinedException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import peerSamplingProtocols.PeerSamplingProtocol;
import peerSamplingProtocols.hyparview.HyparView;
import peerSamplingProtocols.hyparview.channels.MultiLoggerChannelInitializer;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.network.data.Host;
import sybilResistantElection.SybilResistantElection;
import sybilResistantElection.SybilResistantElectionProof;
import utils.InterfaceToIp;
import valueDispatcher.ValueDispatcher;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

import static broadcastProtocols.PeriodicPrunableHashMap.MESSAGE_PRUNE_PERIOD;
import static broadcastProtocols.PeriodicPrunableHashMap.PERIOD_BUFFER_CAPACITY;
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
        Properties props = Babel.loadConfig(args, DEFAULT_CONF);
        redirectOutput(props);
        babel.registerChannelInitializer("SharedTCP", new MultiLoggerChannelInitializer());
        InterfaceToIp.addInterfaceIp(props);
        int port = getNodePort(props);
        Host myself = new Host(InetAddress.getByName(props.getProperty("address")), port);
        logger.info("Hello, I am {} and my contact is {}.", myself, props.getProperty("contact"));
        launchBlockmess(props, myself, babel);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));
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
        var mempoolManager = setUpMempoolManager(props);
        protocols.add(mempoolManager);
        setUpLedgerPrototype(props);
        setUpContentStoragePrototype(props, mempoolManager);
        var ledgerManager = setUpLedgerManager(props, protocols);
        bootstrapContent(props, ledgerManager);
        protocols.add(new SingleElementCommittee<>(ledgerManager));
        setUpSybilElection(props, protocols, ledgerManager);
        recordMetricsBlockmess(props, protocols, ledgerManager);
        initializeSerializers();
        initializeProtocols(props, babel, protocols);
    }

    @NotNull
    private static MempoolManager<StructuredValue<SlimTransaction>, SybilResistantElectionProof> setUpMempoolManager(Properties props)
            throws Exception {
        var bootstrapMod = new MinimalistBootstrapModule();
        var recordMod = new MinimalistRecordModule(props);
        MinimalistChunkCreator<SybilResistantElectionProof> innerChunkCreator = new MinimalistChunkCreator<>();
        var wrapperChunkCreator = new StructuredValueChunkCreator<>(innerChunkCreator);
        return new MempoolManager<>(props, wrapperChunkCreator,recordMod,bootstrapMod);
    }

    private static void setUpSybilElection(Properties props, List<GenericProtocol> protocols, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        protocols.add(new SybilResistantElection<>(props, myKeys, ledgerManager));
    }

    @NotNull
    private static LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> setUpLedgerManager(
            Properties props, List<GenericProtocol> protocols)
            throws PrototypeHasNotBeenDefinedException, HandlerRegistrationException {
        LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager =
                new LedgerManager<>(props);
        var babelLedger = new BabelLedger<>(ledgerManager);
        protocols.add(babelLedger);
        return ledgerManager;
    }

    private static void setUpContentStoragePrototype(Properties props, MempoolManager<StructuredValue<SlimTransaction>, SybilResistantElectionProof> mempoolManager) throws PrototypeAlreadyDefinedException {
        PrototypicalContentStorage<StructuredValue<SlimTransaction>> contentStorage =
                new ContextAwareContentStorage<>(props, mempoolManager);
        ContentStoragePrototype.setPrototype(contentStorage);
    }

    private static void setUpLedgerPrototype(Properties props) throws PrototypeAlreadyDefinedException {
        var blockValidator = new BlockmessGPoETValidator(props);
        var protoLedger = new Blockchain<>(props, blockValidator, new MinimalistBootstrapModule());
        protoLedger.close();
        LedgerPrototype.setPrototype(protoLedger);
    }

    private static void bootstrapContent(Properties props, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager) {
        var txsLoader = new StructuredValuesTxLoader(ledgerManager);
        if (props.getProperty("allowCommonTransactionsAmongChains", "F").equals("F")) {
            loadTxsForBlockmess(props, txsLoader);
            assert(areAllTxsDistinctAmongChains(ledgerManager));
        } else {
            loadTxsCommon(props, ledgerManager);
        }
    }

    private static void recordMetricsBlockmess(Properties props, List<GenericProtocol> protocols, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager) throws IOException, HandlerRegistrationException {
        if (props.getProperty("recordUnfinalized", "T").equals("T"))
            ledgerManager.attachObserver(new UnfinalizedBlocksLog(props));
        if (props.getProperty("recordFinalized", "T").equals("T"))
            ledgerManager.attachObserver(new FinalizedBlocksLog(props));
        if (props.getProperty("recordRepeats", "F").equals("T"))
            protocols.add(new RepeatedTransactionsLog(props));
        if (props.getProperty("recordChangesChains", "F").equals("T"))
            ledgerManager.changesLog.add(new ChangesInNumberOfChainsLog(props));
    }

    private static void initializeSerializers() {
        ProtoPojo.pojoSerializers.put(BlockmessBlockImp.ID, BlockmessBlockImp.serializer);
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
        ProtoPojo.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
        ProtoPojo.pojoSerializers.put(StructuredValue.ID, StructuredValue.serializer);
    }

    private static boolean areAllTxsDistinctAmongChains(LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager) {
        List<UUID> allTxsIds = ledgerManager.getAvailableChains().stream()
                .map(ContentStorage::getStoredContent)
                .flatMap(Collection::stream)
                .map(StructuredValue::getId)
                .collect(Collectors.toList());
        return allTxsIds.size() == new HashSet<>(allTxsIds).size();
    }

    public static List<PublicKey> loadKeys(Properties props)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String allKeysJson = props.getProperty("allKeys", "[]");
        String[] keysLocation = new Gson().fromJson(allKeysJson, String[].class);
        List<PublicKey> keys = new ArrayList<>(keysLocation.length);
        for (String keyLocation : keysLocation)
            keys.add(CryptographicUtils.readECDSAPublicKey(keyLocation));
        return keys;
    }

    private static List<GenericProtocol> addNetworkProtocols(Properties props, Host myself) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>();
        PeerSamplingProtocol peerSamplingProtocol = new HyparView(props, myself);;
        protocols.add((GenericProtocol) peerSamplingProtocol);
        protocols.addAll(addBroadcastProtocols(props, myself, peerSamplingProtocol));
        protocols.add(new ValueDispatcher<>());
        return protocols;
    }

    private static List<GenericProtocol> addBroadcastProtocols(Properties props, Host myself,
                                                               PeerSamplingProtocol peerSamplingProtocol)
            throws HandlerRegistrationException, IOException {
        List<GenericProtocol> protocols = new LinkedList<>();
        PeriodicPrunableHashMap<UUID, LazyValMessage> lazyPrunableHashMap =
                new PeriodicPrunableHashMap<>(MESSAGE_PRUNE_PERIOD, PERIOD_BUFFER_CAPACITY);
        protocols.add(lazyPrunableHashMap);
        PeriodicPrunableHashMap<UUID, EagerValMessage> eagerPrunableHashMap =
                new PeriodicPrunableHashMap<>(MESSAGE_PRUNE_PERIOD, PERIOD_BUFFER_CAPACITY);
        protocols.add(eagerPrunableHashMap);
        protocols.add(new LazyPushBroadcast(props, myself, lazyPrunableHashMap));
        protocols.add(new EagerPushBroadcast(peerSamplingProtocol,
                eagerPrunableHashMap));
        return protocols;
    }

    private static void loadTxsCommon(
            Properties props, LedgerManager<SlimTransaction,
            BlockContent<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> ledgerManager) {
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator(null).generateFakeTxs(numTxs);
        var structuredValues = txs.stream()
                .map(StructuredValueSlimTransactionWrapper::wrapTx)
                .collect(Collectors.toList());
        ledgerManager.getAvailableChains().forEach(b -> b.submitContentDirectly(structuredValues));
    }

    private static void loadTxsForBlockmess(Properties props, TxsLoader<StructuredValue<SlimTransaction>> txsLoader) {
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        var txs = new FakeTxsGenerator(null).generateFakeTxs(numTxs);
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
