package applicationInterface.launcher;

import applicationInterface.ApplicationInterface;
import applicationInterface.GlobalProperties;
import broadcastProtocols.BroadcastValue;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import cmux.AppOperation;
import ledger.BabelLedger;
import ledger.blocks.BlockmessBlockImp;
import ledger.blocks.ContentList;
import ledger.ledgerManager.LedgerManager;
import lombok.SneakyThrows;
import mempoolManager.MempoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import static java.lang.Integer.parseInt;

public class BlockmessLauncher {

	//Creates the logger object
	private static final Logger logger = LogManager.getLogger(BlockmessLauncher.class);
	public static long startTime;

	//Sets the log4j (logging library) configuration file
	static {
		System.setProperty("log4j.configurationFile", "config/log4j2.xml");
	}

	public static void launchBlockmess(String[] args, ApplicationInterface protocol) {
		try {
			tryToLaunchBlockmess(args, protocol);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void tryToLaunchBlockmess(String[] args, ApplicationInterface protocol) throws Exception {
		startTime = System.currentTimeMillis();
		Babel babel = Babel.getInstance();
		Properties props = LauncherCommon.initializeProperties(args);
		GlobalProperties.setProps(props);
		LauncherCommon.redirectOutput();
		babel.registerChannelInitializer("SharedTCP", new MultiLoggerChannelInitializer());
		int port = LauncherCommon.getNodePort(props);
		Host myself = new Host(InetAddress.getByName(props.getProperty("address")), port);
		logger.info("Hello, I am {} and my contact is {}.", myself, props.getProperty("contact"));
		launchBlockmess(myself, babel, List.of(protocol));
		Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));
	}

	private static void launchBlockmess(Host myself, Babel babel, Collection<GenericProtocol> appProtocols) throws Exception {
		List<GenericProtocol> protocols = new LinkedList<>(LauncherCommon.addNetworkProtocols(myself));
		protocols.add(MempoolManager.getSingleton());
		setUpLedgerManager(protocols);
		setUpSybilElection();
		initializeSerializers();
		protocols.addAll(appProtocols);
		LauncherCommon.initializeProtocols(babel, protocols);
	}

	private static void setUpSybilElection() {
		KeyPair myKeys = CryptographicUtils.getNodeKeys();
		new SybilResistantElection(myKeys);
	}

	private static void setUpLedgerManager(List<GenericProtocol> protocols) throws HandlerRegistrationException {
		var babelLedger = new BabelLedger(LedgerManager.getSingleton());
		protocols.add(babelLedger);
	}

	private static void initializeSerializers() {
		BroadcastValue.pojoSerializers.put(BlockmessBlockImp.ID, BlockmessBlockImp.serializer);
		BroadcastValue.pojoSerializers.put(ContentList.ID, ContentList.serializer);
		BroadcastValue.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
		BroadcastValue.pojoSerializers.put(AppOperation.ID, AppOperation.serializer);
	}

}
