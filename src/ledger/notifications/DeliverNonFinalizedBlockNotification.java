package ledger.notifications;

import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverNonFinalizedBlockNotification<B extends LedgerBlock> extends ProtoNotification {

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
