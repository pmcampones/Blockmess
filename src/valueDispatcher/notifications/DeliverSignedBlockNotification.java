package valueDispatcher.notifications;

import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverSignedBlockNotification<B extends LedgerBlock<?,?>>
        extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final B block;

    public DeliverSignedBlockNotification(B block) {
        super(ID);
        this.block = block;
    }

    public B getBlock() {
        return block;
    }
}
