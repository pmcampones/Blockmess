package catecoin.validators;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilResistantElectionProof;

/**
 * Represents an application component that verifies the validity of blocks.
 */
public interface BlockValidator<B extends LedgerBlock<? extends ContentList<? extends IndexableContent>, ? extends SybilResistantElectionProof>> {

    /**
     * Verifies the validity of a block.
     * @param block The block whose validity is ensured.
     * @return True if the block is valid and false otherwise.
     */
    boolean isBlockValid(B block);

}
