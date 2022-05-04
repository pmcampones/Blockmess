package ledger.ledgerManager;

import catecoin.blockConstructors.ContentStorage;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.ledgerManager.nodes.BlockmessChain;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.List;

public interface BlockmessRoot<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        extends ContentStorage<StructuredValue<E>> {

    long getHighestSeenRank();

    /**
     * @return The Chains where blocks should be appended.
     * <p>Temporary Chains unlikely to be expanded are left out from this results.</p>
     */
    List<BlockmessChain<E,C,P>> getAvailableChains();

}
