package sybilResistantCommitteeElection.pos.sortition.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class ExchangeProofPeriodTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public static final long WAIT_PERIOD = 5000;

    public ExchangeProofPeriodTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new ExchangeProofPeriodTimer();
    }

}
