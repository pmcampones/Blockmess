package demo.cryptocurrency;


import demo.cryptocurrency.utxos.UTXO;
import lombok.SneakyThrows;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

import static java.util.stream.Collectors.toList;

public class DBAdapter {

	private static final String DB_PATH = "DB";

	private static DBAdapter singleton = null;

	@SneakyThrows
	private DBAdapter() {
		RocksDB.open(DB_PATH).close();
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
		List<byte[]> encodedIds = ids.parallelStream().map(DBAdapter::uuidToBytes).collect(toList());
		try (RocksDB db = RocksDB.open(DB_PATH)) {
			return db.multiGetAsList(encodedIds)
					.parallelStream()
					.filter(Objects::nonNull)
					.map(DBAdapter::deserializeUtxo).collect(toList());
		}
	}

	@SneakyThrows
	private static UTXO deserializeUtxo(byte[] encodedUtxo) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(encodedUtxo);
			 ObjectInputStream oin = new ObjectInputStream(in)) {
			return (UTXO) oin.readObject();
		}
	}

	@SneakyThrows
	public List<UTXO> getUTXOs(int maxNum) {
		List<byte[]> encodedUtxos = new ArrayList<>();
		try (RocksDB db = RocksDB.open(DB_PATH); RocksIterator it = db.newIterator()) {
			it.seekToFirst();
			while (it.isValid() && encodedUtxos.size() < maxNum) {
				encodedUtxos.add(it.value());
				it.next();
			}
		}
		return encodedUtxos.parallelStream().map(DBAdapter::deserializeUtxo).collect(toList());
	}

	@SneakyThrows
	public List<UTXO> getUTXOs(byte[] startRange, int maxNum) {
		List<byte[]> encodedUtxos = new ArrayList<>();
		try (RocksDB db = RocksDB.open(DB_PATH); RocksIterator it = db.newIterator()) {
			it.seek(startRange);
			while (it.isValid() && encodedUtxos.size() < maxNum) {
				encodedUtxos.add(it.value());
				it.next();
			}
		}
		return encodedUtxos.parallelStream().map(DBAdapter::deserializeUtxo).collect(toList());
	}

	@SneakyThrows
	public void submitUTXOs(Collection<UTXO> utxos) {
		try (RocksDB db = RocksDB.open(DB_PATH)) {
			for (UTXO utxo : utxos) {
				byte[] id = uuidToBytes(utxo.getId());
				byte[] utxoContent = serializeUTXO(utxo);
				db.put(id, utxoContent);
			}
		}
	}

	@SneakyThrows
	private byte[] serializeUTXO(UTXO utxo) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
			 ObjectOutputStream oout = new ObjectOutputStream(out)) {
			oout.writeObject(utxo);
			oout.flush();
			out.flush();
			return out.toByteArray();
		}
	}

}
