package peerSamplingProtocols.hyparview.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class HelloTimer extends ProtoTimer {
    public static final short ID = 5402;

    public HelloTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
