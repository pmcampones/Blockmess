package ledger.notifications;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import sybilResistantElection.SybilElectionProof;
import utils.IDGenerator;

public class DeliverNonFinalizedBlockNotification<B extends LedgerBlock<
        ? extends ContentList<? extends IndexableContent>,? extends SybilElectionProof>>
        extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final B block;

    private final int cumulativeWeight;

    public DeliverNonFinalizedBlockNotification(B block, int cumulativeWeight) {
        super(ID);
        this.block = block;
        this.cumulativeWeight = cumulativeWeight;
    }

    public B getNonFinalizedBlock() {
        return block;
    }

    public int getCumulativeWeight() {
        return cumulativeWeight;
    }

}
