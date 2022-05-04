package catecoin.mempoolManager;

import catecoin.utxos.StorageUTXO;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.ByteBuffer;
import java.util.*;

public class UTXOCollection {

    private static final String DB_PATH = "DB";

    static {
        try {
            RocksDB.open(DB_PATH).close();
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateUtxos(Collection<StorageUTXO> add, Collection<UUID> remove) {
        try (RocksDB db = RocksDB.open(DB_PATH)) {
            for (StorageUTXO utxo : add)
                db.put(uuidToBytes(utxo.getId()), utxo.getSerializedFormat());
            for (UUID rm : remove)
                db.delete(uuidToBytes(rm));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Optional<StorageUTXO>> getUtxos(Collection<UUID> ids) {
        List<Optional<StorageUTXO>> utxos = new ArrayList<>(ids.size());
        try (RocksDB db = RocksDB.openReadOnly(DB_PATH)) {
            for (UUID id : ids) {
                byte[] content = db.get(uuidToBytes(id));
                Optional<StorageUTXO> utxo = content == null ?
                        Optional.empty() : Optional.of(StorageUTXO.fromSerializedFormat(content));
                utxos.add(utxo);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return utxos;
    }

    private static byte[] uuidToBytes(UUID id) {
        return ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

}
