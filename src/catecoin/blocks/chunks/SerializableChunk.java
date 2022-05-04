package catecoin.blocks.chunks;

import catecoin.utxos.JsonAcceptedUTXO;
import catecoin.utxos.StorageUTXO;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

public interface SerializableChunk {

    static Map<UUID, JsonAcceptedUTXO> convertSerializableFormat(Set<StorageUTXO> storageUTXOS) {
        return storageUTXOS.stream()
                .collect(toMap(StorageUTXO::getId, JsonAcceptedUTXO::new));
    }

    static Set<StorageUTXO> convertStorageFormat(Map<UUID, JsonAcceptedUTXO> addedUtxos)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        Set<StorageUTXO> storageUTXOS = new HashSet<>(addedUtxos.size());
        for (JsonAcceptedUTXO ju : addedUtxos.values()) {
            storageUTXOS.add(ju.fromJsonAcceptedUTXO());
        }
        return storageUTXOS;
    }

    MempoolChunk fromSerializableChunk() throws NoSuchAlgorithmException, InvalidKeySpecException;

}
