package demo.cryptocurrency;


import lombok.SneakyThrows;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DBAdapter {

	private static final String DB_PATH = "DB";

	private static final byte[] UTXO_IDS_KEY = "ids".getBytes();

	private static DBAdapter singleton = null;

	private DBAdapter() {
		try {
			RocksDB.open(DB_PATH).close();
		} catch (RocksDBException e) {
			throw new RuntimeException(e);
		}
	}

	public static DBAdapter getSingleton() {
		if (singleton == null)
			singleton = new DBAdapter();
		return singleton;
	}

	@SneakyThrows
	public void deleteUTXOs(Collection<UUID> ids) {
		try (RocksDB db = RocksDB.open(DB_PATH)) {
			for (UUID id : ids) {
				db.delete(uuidToBytes(id));
			}
		}
	}

	private static byte[] uuidToBytes(UUID id) {
		return ByteBuffer.allocate(2 * Long.BYTES)
				.putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits())
				.array();
	}

	@SneakyThrows
	public List<UTXO> getUTXOs(Collection<UUID> ids) {
		try (RocksDB db = RocksDB.open(DB_PATH)) {
			List<UTXO> utxos = new ArrayList<>(ids.size());
			for (UUID id : ids) {
				byte[] utxoBytes = db.get(uuidToBytes(id));
				utxos.add(UTXO.deserializeUTXO(utxoBytes));
			}
			return utxos;
		}
	}

	@SneakyThrows
	public List<UUID> getUTXOIds() {
		try (RocksDB db = RocksDB.open(DB_PATH)) {
			byte[] allIds = db.get(UTXO_IDS_KEY);
			try (ByteArrayInputStream in = new ByteArrayInputStream(allIds);
				 ObjectInputStream oin = new ObjectInputStream(in)) {
				int num_ids = oin.readInt();
				List<UUID> ids = new ArrayList<>(num_ids);
				for (int i = 0; i < num_ids; i++)
					ids.add((UUID) oin.readObject());
				return ids;
			}
		}
	}

}
