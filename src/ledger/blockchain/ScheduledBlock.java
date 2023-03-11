package ledger.blockchain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@Getter
public class ScheduledBlock {

	private final UUID id;

	/**
	 * Identifiers of the blocks that are referenced by this block. In a blockchain the cardinality of this set is
	 * always 1 for any block other than the genesis. In other Ledger implementations, several blocks may be
	 * referenced.
	 */
	private final Set<UUID> previous;

	/**
	 * Identifiers of the blocks that are referencing the current block. Upon the creation of the block this set will be
	 * empty, but will be filled as new blocks are appended to the blockchain. Unlike the field previous, even in a
	 * blockchain the cardinality of this set may be greater than one. On such scenarios the blockchain has forked on
	 * this block.
	 * <p>This field is used mainly facilitate the finalization of blocks.</p>
	 */
	private final Set<UUID> following = new HashSet<>();

}
