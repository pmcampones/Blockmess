package catecoin.notifications;

import catecoin.txs.Transaction;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverIndexableContentNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final Transaction content;

    public DeliverIndexableContentNotification(Transaction content) {
        super(ID);
        this.content = content;
    }

    public Transaction getContent() {
        return content;
    }

}
