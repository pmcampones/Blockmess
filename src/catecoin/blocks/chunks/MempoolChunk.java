package catecoin.blocks.chunks;

import catecoin.utxos.StorageUTXO;

import java.util.Set;
import java.util.UUID;

public interface MempoolChunk {

    UUID getId();

    Set<UUID> getPreviousChunksIds();

    Set<UUID> getRemovedUtxos();

    Set<UUID> getUsedTxs();

    Set<StorageUTXO> getAddedUtxos();

    /**
     * Retrieves the weight of the block, independent of its placement in the Ledger.
     */
    int getInherentWeight();

}
