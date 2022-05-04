package catecoin.validators;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

/**
 * Represents an application component that verifies the validity of blocks.
 */
public interface BlockValidator<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>> {

    /**
     * Verifies the validity of a block.
     * @param block The block whose validity is ensured.
     * @return True if the block is valid and false otherwise.
     */
    boolean isBlockValid(B block);

}
