package demo.cryptocurrency.utxos;

import demo.cryptocurrency.DBAdapter;
import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.File;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a series of UTXOs to bootstrap the system.
 * <p>All nodes running must load the same set of UTXOs in order to ensure the consistency of the application.</p>
 */
public class UTXOGenerator {

	public static final int MAX_AMOUNT_BOOTSTRAP_UTXO = 100000;

	public static void main(String[] args) {
		int numBootstrapUtxos = Integer.parseInt(args[0]);
		String keysPathname = args[1];
		List<PublicKey> keys = readKeysFromFiles(keysPathname);
		Collection<UTXO> utxos = generateUTXOs(keys, numBootstrapUtxos);
		DBAdapter.getSingleton().submitUTXOs(utxos);
	}

	@SneakyThrows
	public static List<UTXO> generateUTXOs(List<PublicKey> keys, int numBootstrapUtxos) {
		List<PublicKey> randomAccessKeys = keys instanceof RandomAccess ? keys : new ArrayList<>(keys);
		List<UTXO> utxos = new ArrayList<>(numBootstrapUtxos);
		for (int i = 0; i < numBootstrapUtxos; i++) {
			Random rand = new Random();
			PublicKey sender = randomAccessKeys.get(rand.nextInt(keys.size()));
			PublicKey owner = randomAccessKeys.get(rand.nextInt(keys.size()));
			int nonce = rand.nextInt();
			int amount = rand.nextInt(MAX_AMOUNT_BOOTSTRAP_UTXO);
			InTransactionUTXO inTx = new InTransactionUTXO(nonce, amount);
			UTXO utxo = UTXOProcessor.processTransactionUTXO(inTx, sender, owner);
			utxos.add(utxo);
		}
		return utxos;
	}

	@SneakyThrows
	private static List<PublicKey> readKeysFromFiles(String repoPathname) {
		List<String> pathnames = readPathnames(repoPathname);
		return pathnames.stream().map(CryptographicUtils::readECDSAPublicKey).collect(Collectors.toList());
	}

	@SneakyThrows
	private static List<String> readPathnames(String repoPathname) {
		File repoFile = new File(repoPathname);
		List<String> pathnames = new ArrayList<>();
		try (Scanner reader = new Scanner(repoFile)) {
			while (reader.hasNextLine()) {
				pathnames.add(reader.nextLine().trim());
			}
		}
		return pathnames;
	}

}
