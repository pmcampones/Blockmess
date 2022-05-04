package peerSamplingProtocols.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class RequireStateRecoveryNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public RequireStateRecoveryNotification() {
        super(ID);
    }

}
