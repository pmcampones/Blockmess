package broadcastProtocols.lazyPush.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class PruneTimer extends ProtoTimer {

    public static final short ID = 321;

    public PruneTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return null;
    }
}
