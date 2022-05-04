package main;

import broadcastProtocols.PeriodicPrunableHashMap;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.eagerPush.EagerValMessage;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import broadcastProtocols.lazyPush.messages.LazyValMessage;
import catecoin.blockConstructors.*;
import catecoin.blocks.SimpleBlockContentList;
import catecoin.clients.AutomatedClient;
import catecoin.clients.InteractiveClient;
import catecoin.mempoolManager.*;
import catecoin.posSpecific.accountManagers.AccountManager;
import catecoin.posSpecific.accountManagers.StatefulAccountManager;
import catecoin.posSpecific.keyBlockManagers.ConstantWeightLedgerKeyBlockManager;
import catecoin.posSpecific.keyBlockManagers.KeyBlockManager;
import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.transactionGenerators.TransactionGenerator;
import catecoin.txs.SlimTransaction;
import catecoin.txs.StructuredValueSlimTransactionWrapper;
import catecoin.validators.*;
import chatApp.ChatApplication;
import com.google.gson.Gson;
import intermediateConsensus.SingleElementCommittee;
import ledger.BabelLedger;
import ledger.Ledger;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlockImp;
import ledger.blocks.LedgerBlock;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.StructuredValue;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeAlreadyDefinedException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import peerSamplingProtocols.PeerSamplingProtocol;
import peerSamplingProtocols.fullMembership.SimpleFullMembership;
import peerSamplingProtocols.hyparview.HyparView;
import peerSamplingProtocols.hyparview.channels.MultiLoggerChannelInitializer;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.network.data.Host;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.drand.PoETWithDRand;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoET;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoETProof;
import sybilResistantCommitteeElection.poet.gpoet.GPoET;
import sybilResistantCommitteeElection.poet.gpoet.GPoETProof;
import sybilResistantCommitteeElection.pos.sortition.PoSAlgorandSortitionWithDRand;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;
import utils.InterfaceToIp;
import valueDispatcher.ValueDispatcher;

import java.io.FileNotFoundException;
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
import static catecoin.mempoolManager.MempoolManagerFactory.getMinimalistMempoolManager;
import static java.lang.Integer.parseInt;

public class Main {

    public static long startTime;

    //Sets the log4j (logging library) configuration file
    static {
        //System.setProperty("logFileName", "log_6000");
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    }

    //Creates the logger object
    private static final Logger logger = LogManager.getLogger(Main.class);

    //Default babel configuration file (can be overridden by the "-config" launch argument)
    public static final String DEFAULT_CONF = "config/config.properties";

    public static final String DEFAULT_TEST_CONF = "config/test_config.properties";

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
        switch (props.getProperty("executionType", "distributedLedger")) {
            case "chat":
                launchChatApplication(props, myself, babel);
                break;
            case "distributedLedger":
                launchTraditionalDistributedLedger(props, myself, babel);
                break;
            case "blockmess":
                launchBlockmess(props, myself, babel);
                break;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));
    }

    private static void redirectOutput(Properties props) throws FileNotFoundException {
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

    private static void launchChatApplication(Properties props, Host myself, Babel babel) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>(addNetworkProtocols(props, myself));
        protocols.add(new ValueDispatcher<>());
        protocols.add(new ChatApplication(props));
        initializeProtocols(props, babel, protocols);
    }

    private static void launchTraditionalDistributedLedger(Properties props, Host myself, Babel babel)
            throws Exception {
        switch (props.getProperty("sybilElectionType", "gpoet")) {
            case "poetDrand":
                launchPoETWithDRandDL(props, myself, babel);
                break;
            case "sortition":
                launchPoSSortitionWithDRandDL(props, myself, babel);
                break;
            case "gpoet":
                launchGPoET(props, myself, babel);
                break;
            default:
                logger.error("Unable to identify launch type. Exiting");
                System.exit(1);
        }
    }

    private static void launchPoETWithDRandDL(Properties props, Host myself, Babel babel) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        List<GenericProtocol> protocols = new LinkedList<>(addNetworkProtocols(props, myself));
        MempoolManager<SlimTransaction,PoETDRandProof> mempoolManager = getMinimalistMempoolManager(props);
        protocols.add(mempoolManager);
        var blockDirector = BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, myKeys);
        GenericProtocol babelBlockDirector = new BabelBlockDirector<>(blockDirector);
        protocols.add(babelBlockDirector);
        TxsLoader<SlimTransaction> txsLoader = new TxLoaderImp(blockDirector);
        Optional<TransactionGenerator> transactionGenerator = loadTxsIfNecessary(props, myKeys, txsLoader);
        transactionGenerator.ifPresent(protocols::add);
        var appValidator = BlockValidatorFactory.getPoETValidator(props, mempoolManager);
        protocols.add((GenericProtocol) appValidator);
        var ledger = new Blockchain<>(props, appValidator, new MinimalistBootstrapModule());
        var babelLedger = new BabelLedger<>(ledger);
        protocols.add(babelLedger);
        protocols.addAll(addCommonDLProtocols(props, myKeys, ledger));
        protocols.add(new PoETWithDRand<>(props, myKeys, ledger, blockDirector));
        initializeProtocols(props, babel, protocols);
    }

    private static void launchGPoET(Properties props, Host myself, Babel babel) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        List<GenericProtocol> protocols = new LinkedList<>(addNetworkProtocols(props, myself));
        MempoolManager<SlimTransaction, GPoETProof> mempoolManager = getMinimalistMempoolManager(props);
        protocols.add(mempoolManager);
        var blockDirector = BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, myKeys);
        GenericProtocol babelBlockDirector = new BabelBlockDirector<>(blockDirector);
        protocols.add(babelBlockDirector);
        TxsLoader<SlimTransaction> txsLoader = new TxLoaderImp(blockDirector);
        Optional<TransactionGenerator> transactionGenerator = loadTxsIfNecessary(props, myKeys, txsLoader);
        transactionGenerator.ifPresent(protocols::add);
        var appValidator = new GPoETValidator(props);
        var ledger = new Blockchain<>(props, appValidator, new MinimalistBootstrapModule());
        var babelLedger = new BabelLedger<>(ledger);
        protocols.add(babelLedger);
        protocols.addAll(addCommonDLProtocols(props, myKeys, ledger));
        protocols.add(new GPoET<>(props, ledger, blockDirector, myKeys));
        ledger.attachObserver(new UnfinalizedBlocksLog(props));
        ledger.attachObserver(new FinalizedBlocksLog(props));
        initializeProtocols(props, babel, protocols);
    }

    private static void launchPoSSortitionWithDRandDL(Properties props, Host myself, Babel babel)
            throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        List<GenericProtocol> protocols = new LinkedList<>(addNetworkProtocols(props, myself));
        MempoolManager<SlimTransaction, SortitionProof> mempoolManager =
                MempoolManagerFactory.getRichMempoolManager(props);
        protocols.add(mempoolManager);
        BlockDirector<SlimTransaction, BlockContent<SlimTransaction>, LedgerBlock<BlockContent<SlimTransaction>, SortitionProof>, SortitionProof> blockDirector =
                BlockDirectorKits.getSimpleLedgerBlock(props, mempoolManager, myKeys);
        protocols.add((GenericProtocol) blockDirector);
        TxsLoader<SlimTransaction> txsLoader = new TxLoaderImp(blockDirector);
        Optional<TransactionGenerator> transactionGenerator =
                loadTxsIfNecessary(props, myKeys, txsLoader);
        transactionGenerator.ifPresent(protocols::add);
        AccountManager accountManager = new StatefulAccountManager<>(props, mempoolManager);
        protocols.add((GenericProtocol) accountManager);
        KeyBlockManager keyBlockManager = new ConstantWeightLedgerKeyBlockManager<>(props);
        protocols.add((GenericProtocol) keyBlockManager);
        SybilProofValidator<SortitionProof> proofValidator = new SortitionProofValidator(props, accountManager);
        BlockValidator<LedgerBlock<BlockContent<SlimTransaction>, SortitionProof>> appValidator =
                new KeyAndMicroBlocksValidator(props, mempoolManager, proofValidator);
        protocols.add((GenericProtocol) appValidator);
        Ledger<LedgerBlock<BlockContent<SlimTransaction>, SortitionProof>> ledger =
                new Blockchain<>(props, appValidator, new RichBootstrapModule());
        BabelLedger<LedgerBlock<BlockContent<SlimTransaction>, SortitionProof>> babelLedger = new BabelLedger<>(ledger);
        protocols.add(babelLedger);
        GenericProtocol posSortitionElection =
                new PoSAlgorandSortitionWithDRand<>(props, myKeys, accountManager,
                keyBlockManager, ledger, blockDirector);
        protocols.add(posSortitionElection);
        protocols.addAll(addCommonDLProtocols(props, myKeys, ledger));
        ledger.attachObserver(new UnfinalizedBlocksLog(props));
        ledger.attachObserver(new FinalizedBlocksLog(props));
        initializeProtocols(props, babel, protocols);
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
    private static MempoolManager<StructuredValue<SlimTransaction>, BlockmessGPoETProof> setUpMempoolManager(Properties props)
            throws Exception {
        var bootstrapMod = new MinimalistBootstrapModule();
        var recordMod = new MinimalistRecordModule(props);
        MinimalistChunkCreator<BlockmessGPoETProof> innerChunkCreator = new MinimalistChunkCreator<>();
        var wrapperChunkCreator = new StructuredValueChunkCreator<>(innerChunkCreator);
        return new MempoolManager<>(props, wrapperChunkCreator,recordMod,bootstrapMod);
    }

    private static void setUpSybilElection(Properties props, List<GenericProtocol> protocols, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager) throws Exception {
        KeyPair myKeys = CryptographicUtils.getNodeKeys(props);
        protocols.add(new BlockmessGPoET<>(props, myKeys, ledgerManager));
    }

    @NotNull
    private static LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> setUpLedgerManager(
            Properties props, List<GenericProtocol> protocols)
            throws PrototypeHasNotBeenDefinedException, HandlerRegistrationException {
        LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager =
                new LedgerManager<>(props);
        var babelLedger = new BabelLedger<>(ledgerManager);
        protocols.add(babelLedger);
        return ledgerManager;
    }

    private static void setUpContentStoragePrototype(Properties props, MempoolManager<StructuredValue<SlimTransaction>, BlockmessGPoETProof> mempoolManager) throws PrototypeAlreadyDefinedException {
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

    private static void bootstrapContent(Properties props, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager) {
        var txsLoader = new StructuredValuesTxLoader(ledgerManager);
        if (props.getProperty("allowCommonTransactionsAmongChains", "F").equals("F")) {
            loadTxsForBlockmess(props, txsLoader);
            assert(areAllTxsDistinctAmongChains(ledgerManager));
        } else {
            loadTxsCommon(props, ledgerManager);
        }
    }

    private static void recordMetricsBlockmess(Properties props, List<GenericProtocol> protocols, LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager) throws IOException, HandlerRegistrationException {
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
        ProtoPojo.pojoSerializers.put(BlockmessGPoETProof.ID, BlockmessGPoETProof.serializer);
        ProtoPojo.pojoSerializers.put(StructuredValue.ID, StructuredValue.serializer);
    }

    private static boolean areAllTxsDistinctAmongChains(LedgerManager<SlimTransaction, BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager) {
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

    private static Collection<SlimTransaction> getBootstrapTxs(Properties props, KeyPair myKeys,
                                                               List<PublicKey> nodes, TxsLoader txsLoader)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (props.getProperty("loadTxs", "F").equals("T")) {
            return loadBootstrapTxsFromFile(props, txsLoader);
        } else {
            return generateNewBootstrapTxs(props, myKeys, nodes);
        }
    }

    private static List<GenericProtocol> addNetworkProtocols(Properties props, Host myself) throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>();
        PeerSamplingProtocol peerSamplingProtocol = getPeerSamplingProtocol(props, myself);
        protocols.add((GenericProtocol) peerSamplingProtocol);
        protocols.addAll(addBroadcastProtocols(props, myself, peerSamplingProtocol));
        protocols.add(new ValueDispatcher<>());
        return protocols;
    }

    private static <P extends SybilElectionProof> List<GenericProtocol> addCommonDLProtocols(
            Properties props, KeyPair myKeys, Ledger<LedgerBlock<BlockContent<SlimTransaction>, P>> ledger)
            throws Exception {
        List<GenericProtocol> protocols = new LinkedList<>();
        protocols.add(new ValueDispatcher<>());
        protocols.add(new SingleElementCommittee<>(ledger));
        protocols.add(getDLClient(props, myKeys.getPublic()));
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
        protocols.add(new EagerPushBroadcast(myself, peerSamplingProtocol,
                eagerPrunableHashMap));
        return protocols;
    }

    private static void loadTxsCommon(
            Properties props, LedgerManager<SlimTransaction,
            BlockContent<StructuredValue<SlimTransaction>>, BlockmessGPoETProof> ledgerManager) {
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

    private static Optional<TransactionGenerator> loadTxsIfNecessary(Properties props, KeyPair myKeys,
                                                                     TxsLoader<SlimTransaction> txsLoader)
            throws Exception {
        if (props.getProperty("simplifiedTxs", "F").equals("T")) {
            List<PublicKey> nodes = loadKeys(props);
            Collection<SlimTransaction> txs = getBootstrapTxs(props, myKeys, nodes, txsLoader);
            txsLoader.loadTxs(txs);
            return Optional.empty();
        } else {
            return Optional.of(new TransactionGenerator(props, myKeys));
        }
    }

    private static Collection<SlimTransaction> generateNewBootstrapTxs(Properties props, KeyPair myKeys, List<PublicKey> nodes) {
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        return new FakeTxsGenerator(myKeys)
                .generateFakeTxs(nodes, numTxs);
    }

    private static Collection<SlimTransaction> loadBootstrapTxsFromFile(Properties props, TxsLoader txsLoader)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String filepath = props.getProperty("loadTxsLocation");
        return txsLoader.loadFromFile(filepath);
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

    private static PeerSamplingProtocol getPeerSamplingProtocol(Properties props, Host myself)
            throws Exception {
        switch (props.getProperty("peerSamplingProtocol", "hyparview")) {
            case "simpleFull":
                return new SimpleFullMembership(props, myself);
            case "hyparview":
                return new HyparView(props, myself);
            default:
                logger.error("Cannot identify the Peer Sampling Protocol. Exiting");
                System.exit(1);
                throw new Exception();
        }
    }

    private static GenericProtocol getDLClient(Properties props, PublicKey myKey) throws Exception {
        switch (props.getProperty("client", "interactive")) {
            case "interactive":
                return new InteractiveClient(props, myKey);
            case "automated":
                return new AutomatedClient(props, myKey);
            default:
                System.err.println("Intended client protocol does not exist");
                System.exit(1);
                throw new Exception();
        }
    }

}
