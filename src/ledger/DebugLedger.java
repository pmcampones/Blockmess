package ledger;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilElectionProof;

import java.util.Set;
import java.util.UUID;

public interface DebugLedger<B extends LedgerBlock<? extends ContentList<? extends IndexableContent>,? extends SybilElectionProof>>
        extends Ledger<B> {

    int getFinalizedWeight();

    Set<UUID> getFinalizedIds();

    Set<UUID> getNodesIds();

    Set<UUID> getForkBlocks(int depth);
}
