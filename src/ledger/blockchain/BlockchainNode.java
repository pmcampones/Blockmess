package ledger.blockchain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Node in the DAG topology that comprises the Ledger component.
 * <p>Does not contain the content specific to the application,
 * but instead has information specific to the finalization of blocks.</p>
 */
public class BlockchainNode {

    private final UUID blockId;

    private final Set<UUID> previous;

    private final Set<UUID> following = new HashSet<>();

    private final int weight;

    public BlockchainNode(UUID blockId, Set<UUID> previous, int weight) {
        this.blockId = blockId;
        this.previous = previous;
        this.weight = weight;
    }

    public UUID getBlockId() {
        return blockId;
    }

    /**
     * Identifiers of the blocks that are referenced by this block. In a blockchain the cardinality of this set is
     * always 1 for any block other than the genesis. In other Ledger implementations, several blocks may be
     * referenced.
     */
    public Set<UUID> getPrevious() {
        return previous;
    }

    /**
     * Identifiers of the blocks that are referencing the current block. Upon the creation of the block this set will be
     * empty, but will be filled as new blocks are appended to the blockchain. Unlike the field previous, even in a
     * blockchain the cardinality of this set may be greater than one. On such scenarios the blockchain has forked on
     * this block.
     */
    public Set<UUID> getFollowing() {
        return following;
    }

    /**
     * Weight of the block in the Blockchain. In most Ledger implementations the concept of weight is not useful, as all
     * blocks have a weight of 1. However, this concept of attributing weights to blocks can be used to simplify systems
     * that would not follow our model otherwise. For example, in BitcoinNG the finalization of blocks are mostly
     * decided by the KeyBlocks (opposite to the Microblocks). We can use the same Blockchain for BitcoinNG as for more
     * traditional Cryptocurrency applications simply by giving the KeyBlocks a far larger weight than the Microblocks
     * and set the finalization weight to twice the weight of a KeyBlock. Additionally, the weights can be used to
     * enforce priority rules enforced by a bacon chain in sharding DLs. The beacon chains determine that a given block
     * must be finalized in all shards involved in its proposal, even if the finalization of the block is against the
     * rules of the finalization algorithm (longest chain, ghost, phantom, etc.); We can reverse this incongruent
     * behaviour simply by attributing a very large weight to these cross shard blocks.
     */
    public int getWeight() {
        return weight;
    }
}
