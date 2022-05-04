package catecoin.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class GenerateTransactionTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public GenerateTransactionTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new GenerateTransactionTimer();
    }
}
