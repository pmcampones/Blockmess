package demo.cryptocurrency.client;

import demo.cryptocurrency.KeyLoader;
import demo.cryptocurrency.utxos.UTXO;
import lombok.SneakyThrows;

import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class AutomatedClient {

	private static final int MAX_TX_VALUE = 100;

	public static void main(String[] args) {
		CryptocurrencyClient client = new CryptocurrencyClient(sliceArgs(args));
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


}
