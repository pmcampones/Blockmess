package demo.cryptocurrency.client;

import demo.cryptocurrency.KeyLoader;
import demo.cryptocurrency.utxos.UTXO;
import lombok.SneakyThrows;

import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class AutomatedClient {

	private static final int MAX_TX_VALUE = 100;

	static {
		System.setProperty("log4j.configurationFile", "config/log4j2.xml");
	}

	public static void main(String[] args) {
		if (args.length < 2)
			printUsageMessage();
		else
			execute(args);
	}

	private static void execute(String[] args) {
		var overwrittenProperties = sliceArgs(args);
		CryptocurrencyClient client = new CryptocurrencyClient(overwrittenProperties);
		int proposalInterval = Integer.parseInt(args[0]);
		List<PublicKey> allKeys = KeyLoader.readKeysFromFiles(args[1]);
		List<PublicKey> keys = filterMyKey(allKeys, client.getMyKeys().getPublic());
		proposeTransactions(client, proposalInterval, keys);
	}

	private static String[] sliceArgs(String[] ogArgs) {
		String[] slicedArgs = new String[ogArgs.length - 2];
		System.arraycopy(ogArgs, 2, slicedArgs, 0, slicedArgs.length);
		return slicedArgs;
	}

	private static List<PublicKey> filterMyKey(List<PublicKey> allKeys, PublicKey myKey) {
		byte[] myEncoded = myKey.getEncoded();
		return allKeys.stream().filter(k -> !Arrays.equals(k.getEncoded(), myEncoded)).collect(Collectors.toList());
	}

	@SneakyThrows
	public static void proposeTransactions(CryptocurrencyClient client, int proposalInterval, List<PublicKey> keys) {
		Random rand = new Random();
		Iterator<UTXO> myUtxosIt = new ArrayList<>(client.getMyUTXOs().values()).iterator();
		Iterator<PublicKey> keyIterator = keys.iterator();
		while (myUtxosIt.hasNext()) {
			if (!keyIterator.hasNext())
				keyIterator = keys.iterator();
			PublicKey destination = keyIterator.next();
			int amount = rand.nextInt(MAX_TX_VALUE);
			client.submitTx(destination, amount, operationResult -> {});
			Thread.sleep(rand.nextInt(2 * proposalInterval));
		}
		System.err.println("Left the Transaction proposal loop. No more Txs to propagate");
	}

	private static void printUsageMessage() {
		System.out.println("Missing required arguments:");
		System.out.println("Usage: java -cp demo.cryptocurrency.AutomatedClient proposal_interval keys_file [property=value]*");
		System.out.println("proposal_interval: Average time interval between transaction proposals for each node.");
		System.out.println("keys_file: File containing all public keys of the nodes in the system.");
		System.out.println("[property=value]*: List of property values to override those in the configuration file.");
	}

}
