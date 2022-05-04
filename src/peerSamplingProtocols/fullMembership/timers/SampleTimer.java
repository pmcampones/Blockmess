package peerSamplingProtocols.fullMembership.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class SampleTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public SampleTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
