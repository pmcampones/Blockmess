package ledger.blockchain;

import lombok.Getter;

import java.util.Set;
import java.util.UUID;

/**
 * Node in the DAG topology that comprises the Ledger component.
 * <p>Does not contain the content specific to the application,
 * but instead has information specific to the finalization of blocks.</p>
 */
@Getter
public class BlockchainNode extends ScheduledBlock {


	/**
	 * Weight of the block in the Blockchain. In most Ledger implementations the concept of weight is not useful, as all
	 * blocks have a weight of 1. However, this concept of attributing weights to blocks can be used to simplify systems
	 * that would not follow our model otherwise. For example, in BitcoinNG the finalization of blocks are mostly
	 * decided by the KeyBlocks (opposite to the Microblocks). We can use the same Blockchain for BitcoinNG as for more
	 * traditional AutomatedClient applications simply by giving the KeyBlocks a far larger weight than the Microblocks
	 * and set the finalization weight to twice the weight of a KeyBlock. Additionally, the weights can be used to
	 * enforce priority rules enforced by a bacon chain in sharding DLs. The beacon chains determine that a given block
	 * must be finalized in all shards involved in its proposal, even if the finalization of the block is against the
	 * rules of the finalization algorithm (longest chain, ghost, phantom, etc.); We can reverse this incongruent
	 * behaviour simply by attributing a very large weight to these cross shard blocks.
	 */
	private final int weight;

	public BlockchainNode(UUID id, Set<UUID> previous, int weight) {
		super(id, previous);
		this.weight = weight;
	}
}
