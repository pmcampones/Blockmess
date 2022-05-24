package applicationInterface;

import cmux.AppOperation;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.List;

public class DeliverFinalizedContentNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final List<AppOperation> finalizedContent;

    public DeliverFinalizedContentNotification(List<AppOperation> finalizedContent) {
        super(ID);
        this.finalizedContent = finalizedContent;
    }

    public List<AppOperation> getFinalizedContent() {
        return finalizedContent;
    }
}
