package catecoin.blocks.chunks;

import catecoin.utxos.StorageUTXO;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

public class MempoolChunk {

    private final UUID stateID;

    private final Set<UUID> previous;

    private final Map<UUID, StorageUTXO> addedUtxos;

    private final Set<UUID> removedUtxos;

    private final Set<UUID> usedTxs;

    private final int weight;

    public MempoolChunk(UUID stateID, Set<UUID> previous, Set<StorageUTXO> addedUtxos,
                        Set<UUID> removedUtxos, Set<UUID> usedTxs, int weight) {
        this.stateID = stateID;
        this.previous = previous;
        this.addedUtxos = addedUtxos.stream().collect(toMap(StorageUTXO::getId, u -> u));
        this.removedUtxos = removedUtxos;
        this.usedTxs = usedTxs;
        this.weight = weight;
    }

    public UUID getId() {
        return stateID;
    }

    public Set<UUID> getPreviousChunksIds() {
        return previous;
    }

    public Set<UUID> getRemovedUtxos() {
        return removedUtxos;
    }

    public Set<UUID> getUsedTxs() {
        return usedTxs;
    }

    public Set<StorageUTXO> getAddedUtxos() {
        return Set.copyOf(addedUtxos.values());
    }

    public int getInherentWeight() {
        return weight;
    }

}
