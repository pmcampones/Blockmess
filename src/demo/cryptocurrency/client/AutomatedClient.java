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
		System.out.println("YO IT ACTUALLY DID SOMETHING");
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
			client.submitTx(destination, amount, operationResult -> {
			});
			Thread.sleep(proposalInterval);
		}
	}

	private static void printUsageMessage() {
		System.out.println("Missing required arguments:");
		System.out.println("Usage: java -cp demo.counter.AsyncCounter counter_change num_updates [property=value]*");
		System.out.println("counter_change: Update to the value of the shared counter in each update.");
		System.out.println("num_updates: Number of updates to be executed asynchronously.");
		System.out.println("[property=value]*: List of property values to override those in the configuration file.");
	}


}
