package ledger.ledgerManager.nodes;

import catecoin.txs.IndexableContent;
import ledger.DebugLedger;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.List;

public interface DebugBlockmessChain<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        extends BlockmessChain<E,C,P>, DebugLedger<BlockmessBlock<C,P>> {

    int getNumSamples();

    int getNumUnderloaded();

    int getNumOverloaded();

    int getFinalizedWeight();

    boolean isOverloaded();

    int getMaxBlockSize();

    boolean hasTemporaryChains();

    int getNumChaining();

    /**
     * Retrieves how many Chains have been generated from this Chain,
     * whether they are permanent or temporary.
     */
    int getNumSpawnedChains();

    List<DebugBlockmessChain<E,C,P>> getSpawnedChains();

    int getNumFinalizedPending();

}
