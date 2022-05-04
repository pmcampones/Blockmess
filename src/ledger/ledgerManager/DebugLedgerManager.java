package ledger.ledgerManager;

import catecoin.txs.IndexableContent;
import ledger.DebugLedger;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.nodes.BlockmessChain;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public interface DebugLedgerManager<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        extends DebugLedger<BlockmessBlock<C,P>> {

    Map<UUID,BlockmessChain<E,C,P>> getChains();

    BlockmessChain<E,C,P> getOrigin();

    /**
     * Creates an insertion order iterator for the Chains in the system.
     * <p>Is this necessary if we already have getChains?
     * It shouldn't be, but using an iterator on the result of
     * getChains has given me a veeeery annoying bug (for no reason whatsoever)
     * and left me pretty angry.</p>
     * <p>In sum, yes, it is necessary. Please do not use an iterator on the results of getChains.</p>
     */
    Iterator<BlockmessChain<E,C,P>> getChainIt();

    int getMaxBlockSize();

    int getNumSamples();

}
