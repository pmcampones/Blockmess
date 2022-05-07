package ledger;

import ledger.blocks.BlockmessBlock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Objects implementing this interface subscribe to changes in a Ledger
 * object according to the Observer design pattern.
 */
public interface LedgerObserver {

    void deliverNonFinalizedBlock(BlockmessBlock block, int weight);

    void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded);

}
