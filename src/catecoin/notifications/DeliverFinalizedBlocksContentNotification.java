package catecoin.notifications;

import catecoin.utxos.StorageUTXO;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.Set;
import java.util.UUID;

public class DeliverFinalizedBlocksContentNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final Set<StorageUTXO> removedUtxos;

    private final Set<StorageUTXO> addedUtxos;

    private final Set<UUID> usedTxs;

    public DeliverFinalizedBlocksContentNotification(Set<StorageUTXO> removedUtxos, Set<StorageUTXO> addedUtxos,
                                                     Set<UUID> usedTxs) {
        super(ID);
        this.removedUtxos = removedUtxos;
        this.addedUtxos = addedUtxos;
        this.usedTxs = usedTxs;
    }

    public Set<StorageUTXO> getRemovedUtxo() {
        return removedUtxos;
    }

    public Set<StorageUTXO> getAddedUtxos() {
        return addedUtxos;
    }

    public Set<UUID> getUsedTxs() {
        return usedTxs;
    }
}
