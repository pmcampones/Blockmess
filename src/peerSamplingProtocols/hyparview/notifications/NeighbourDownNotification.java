package peerSamplingProtocols.hyparview.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;

public class NeighbourDownNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final Host neighbour;

    public NeighbourDownNotification(Host neighbour) {
        super(ID);
        this.neighbour = neighbour;
    }

    public Host getNeighbour() {
        return neighbour;
    }
}
