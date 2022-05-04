package peerSamplingProtocols.hyparview.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

public class JoinTimer extends ProtoTimer {

    public static final short ID = 5533;

    private final Host contact;

    private int attempts = 1;

    public JoinTimer(Host contact) {
        super(ID);
        this.contact = contact;
    }

    @Override
    public ProtoTimer clone() {
        return new JoinTimer(contact);
    }

    public Host getContact() {
        return contact;
    }

    public void incAttempts() {
        attempts++;
    }

    public int getAttempts() {
        return attempts;
    }
}
