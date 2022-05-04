package catecoin.validators;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

/**
 * Simple Validator to test the Ledger protocol without having to create complicated blocks.
 */
public class TestBlockValidator<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        implements BlockValidator<B>{


    @Override
    public boolean isBlockValid(B block) {
        return true;
    }
}
