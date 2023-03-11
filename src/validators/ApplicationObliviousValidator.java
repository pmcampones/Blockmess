package validators;

import ledger.blocks.BlockmessBlock;

public interface ApplicationObliviousValidator {
    boolean isBlockValid(BlockmessBlock block);

    boolean isProofValid(BlockmessBlock block);
}
