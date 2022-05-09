package mempoolManager.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DeliverFinalizedBlockIdentifiersNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    /**
     * Blocks that have been finalized by the Ledger
     */
    private final List<UUID> finalized;

    /**
     * When some blocks are finalized, others are removed because they belong in stale forks.
     * State related to these blocks must be addressed.
     */
    private final Set<UUID> discarded;

    public DeliverFinalizedBlockIdentifiersNotification(Set<UUID> discarded, List<UUID> finalized) {
        super(ID);
        this.discarded = discarded;
        this.finalized = finalized;
    }

    /**
     * Gets the finalized blocks in the correct order.
     */
    public List<UUID> getFinalizedBlocksIds() {
        return finalized;
    }

    public Set<UUID> getDiscardedBlockIds() {
        return discarded;
    }

}
