package ledger.blocks;

import java.util.UUID;


public interface BlockmessBlock extends LedgerBlock {
	UUID getDestinationChain();

	long getBlockRank();

	long getNextRank();
}
