package ledger;

import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.ProtoPojo;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Objects implementing this interface subscribe to changes in a Ledger
 * object according to the Observer design pattern.
 * @param <B> The type of blocks used by the Subject Ledger.
 */
public interface LedgerObserver<B extends LedgerBlock<? extends BlockContent<? extends ProtoPojo>,? extends SybilElectionProof>> {

    void deliverNonFinalizedBlock(B block, int weight);

    void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded);

}
