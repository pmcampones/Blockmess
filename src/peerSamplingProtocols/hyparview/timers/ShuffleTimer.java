package peerSamplingProtocols.hyparview.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class ShuffleTimer extends ProtoTimer {
    public static final short ID = 5401;

    public ShuffleTimer() {
        super(ShuffleTimer.ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
