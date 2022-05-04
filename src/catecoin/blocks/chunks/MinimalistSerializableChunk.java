package catecoin.blocks.chunks;

import catecoin.utxos.JsonAcceptedUTXO;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MinimalistSerializableChunk implements SerializableChunk {

    private final UUID stateID;

    private final Set<UUID> previous;

    private final Map<UUID, JsonAcceptedUTXO> addedUtxos;

    private final Set<UUID> removedUtxos;

    private final Set<UUID> usedTxs;

    private final int weight;

    public MinimalistSerializableChunk(MinimalistMempoolChunk chunk) {
        this.stateID = chunk.getId();
        this.previous = chunk.getPreviousChunksIds();
        this.addedUtxos = SerializableChunk.convertSerializableFormat(chunk.getAddedUtxos());
        this.removedUtxos = chunk.getRemovedUtxos();
        this.usedTxs = chunk.getUsedTxs();
        this.weight = chunk.getInherentWeight();
    }

    @Override
    public MempoolChunk fromSerializableChunk() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return new MinimalistMempoolChunk(stateID, previous,
                SerializableChunk.convertStorageFormat(addedUtxos),
                removedUtxos, usedTxs, weight);
    }

}
