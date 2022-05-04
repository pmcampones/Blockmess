package broadcastProtocols.lazyPush.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class DelayedResponsesTimer extends ProtoTimer {

    public static final short ID = 943;

    public DelayedResponsesTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return null;
    }
}
