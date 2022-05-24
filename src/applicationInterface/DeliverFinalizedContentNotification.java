package applicationInterface;

import cmux.AppContent;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.List;

public class DeliverFinalizedContentNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final List<AppContent> finalizedContent;

    public DeliverFinalizedContentNotification(List<AppContent> finalizedContent) {
        super(ID);
        this.finalizedContent = finalizedContent;
    }

    public List<AppContent> getFinalizedContent() {
        return finalizedContent;
    }
}
