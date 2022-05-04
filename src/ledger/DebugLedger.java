package ledger;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.Set;
import java.util.UUID;

public interface DebugLedger<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>,? extends SybilElectionProof>>
        extends Ledger<B> {

    int getFinalizedWeight();

    Set<UUID> getFinalizedIds();

    Set<UUID> getNodesIds();

    Set<UUID> getForkBlocks(int depth);
}
