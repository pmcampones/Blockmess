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

public class SerializableChunk {

    private final UUID stateID;

    private final Set<UUID> previous;

    private final Map<UUID, JsonAcceptedUTXO> addedUtxos;

    private final Set<UUID> removedUtxos;

    private final Set<UUID> usedTxs;

    private final int weight;

    public SerializableChunk(MempoolChunk chunk) {
        this.stateID = chunk.getId();
        this.previous = chunk.getPreviousChunksIds();
        this.addedUtxos = SerializableChunk.convertSerializableFormat(chunk.getAddedUtxos());
        this.removedUtxos = chunk.getRemovedUtxos();
        this.usedTxs = chunk.getUsedTxs();
        this.weight = chunk.getInherentWeight();
    }

    public static Map<UUID, JsonAcceptedUTXO> convertSerializableFormat(Set<StorageUTXO> storageUTXOS) {
        return storageUTXOS.stream()
                .collect(toMap(StorageUTXO::getId, JsonAcceptedUTXO::new));
    }

    public MempoolChunk fromSerializableChunk() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return new MempoolChunk(stateID, previous,
                SerializableChunk.convertStorageFormat(addedUtxos),
                removedUtxos, usedTxs, weight);
    }

    public static Set<StorageUTXO> convertStorageFormat(Map<UUID, JsonAcceptedUTXO> addedUtxos)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        Set<StorageUTXO> storageUTXOS = new HashSet<>(addedUtxos.size());
        for (JsonAcceptedUTXO ju : addedUtxos.values()) {
            storageUTXOS.add(ju.fromJsonAcceptedUTXO());
        }
        return storageUTXOS;
    }

}
