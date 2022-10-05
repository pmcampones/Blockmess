package demo.cryptocurrency;

import java.util.List;
import java.util.UUID;

public class AutomatedClient {

	public static void main(String[] args) {
		float throughput = Float.parseFloat(args[0]);
		CryptocurrencyClient client = new CryptocurrencyClient(args);
		proposeTransactions(System.currentTimeMillis(), throughput, DBAdapter.getSingleton().getUTXOIds());
	}

	public static void proposeTransactions(long startTime, float throughput, List<UUID> ids) {

	}


}
