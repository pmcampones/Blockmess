package catecoin.notifications;

import catecoin.txs.IndexableContent;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverIndexableContentNotification<E extends IndexableContent> extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final E content;

    public DeliverIndexableContentNotification(E content) {
        super(ID);
        this.content = content;
    }

    public E getContent() {
        return content;
    }

}
