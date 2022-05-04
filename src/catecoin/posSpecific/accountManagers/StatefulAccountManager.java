package catecoin.posSpecific.accountManagers;

import catecoin.mempoolManager.MempoolManager;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.SybilElectionProof;
import utils.IDGenerator;

import java.io.IOException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Integer.parseInt;

public class StatefulAccountManager<P extends SybilElectionProof> extends GenericProtocol implements AccountManager {

    private static final Logger logger = LogManager.getLogger(StatefulAccountManager.class);

    public static final short ID = IDGenerator.genId();

    private final MempoolManager<SlimTransaction, P> mempoolManager;

    private final ReadWriteLock accountsLock = new ReentrantReadWriteLock();

    private final Map<PublicKey, Map<UUID, StorageUTXO>> accounts = new HashMap<>();

    private final int numberCoins;

    public StatefulAccountManager(Properties props, MempoolManager<SlimTransaction, P> mempoolManager) throws HandlerRegistrationException {
        super(StatefulAccountManager.class.getSimpleName(), ID);
        this.mempoolManager = mempoolManager;
        subscribeNotification(DeliverFinalizedBlocksContentNotification.ID,
                this::uponDeliverFinalizedBlocksContentNotification);
        numberCoins = parseInt(props.getProperty("numberCoins",
                String.valueOf(MempoolManager.NUMBER_COINS)));
        //bootstrapAccounts();
    }

    /*private void bootstrapAccounts() {
        Map<UUID, StorageUTXO> utxos = new HashMap<>(mempoolManager.utxos);
        for (StorageUTXO utxo : utxos.values()) {
            Map<UUID, StorageUTXO> accountUtxos = accounts
                    .computeIfAbsent(utxo.getUTXOOwner(), k -> new HashMap<>());
            accountUtxos.put(utxo.getId(), utxo);
        }
        assert(doAmountsMatch());
    }*/

    private boolean doAmountsMatch() {
        return accounts.values().stream()
                .flatMap(p -> p.values().stream())
                .mapToInt(StorageUTXO::getAmount).sum()
                == numberCoins;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public int getCirculationCoins() {
        return numberCoins;
    }

    /**
     * This can have concurrency problems if a block is finalized as the transitionCoins are being computed.
     */
    @Override
    public int getProposerCoins(PublicKey node, UUID block) {
        try {
            mempoolManager.getMempoolReadLock().lock();
            return tryToGetProposerCoins(node, block);
        } finally {
            mempoolManager.getMempoolReadLock().unlock();
        }
    }

    private int tryToGetProposerCoins(PublicKey node, UUID block) {
        Map<UUID, StorageUTXO> accountUtxos = new HashMap<>(accounts.getOrDefault(node, new HashMap<>()));
        mempoolManager.getValidUtxosFromChunk(block)
                .stream().filter(utxo -> utxo.getUTXOOwner().equals(node))
                .forEach(utxo -> accountUtxos.put(utxo.getId(), utxo));
        mempoolManager.getInvalidUtxosFromChunk(block, new HashSet<>())
                .forEach(accountUtxos::remove);
        return accountUtxos.values().stream().mapToInt(StorageUTXO::getAmount).sum();
    }

    private void uponDeliverFinalizedBlocksContentNotification(DeliverFinalizedBlocksContentNotification notif,
                                                               short source) {
        updateBalances(notif.getAddedUtxos(), notif.getRemovedUtxo());
    }

    private void updateBalances(Set<StorageUTXO> added, Set<StorageUTXO> removed) {
        try {
            accountsLock.writeLock().lock();
            addUtxosAccounts(added);
            removeUtxosAccounts(removed);
        } finally {
            accountsLock.writeLock().unlock();
        }
        assert(doAmountsMatch());
    }

    private void addUtxosAccounts(Set<StorageUTXO> added) {
        for (StorageUTXO utxo : added) {
            Map<UUID, StorageUTXO> accountUtxos = accounts
                    .computeIfAbsent(utxo.getUTXOOwner(), k -> new HashMap<>());
            accountUtxos.put(utxo.getId(), utxo);
        }
    }

    private void removeUtxosAccounts(Set<StorageUTXO> removed) {
        for (StorageUTXO utxo : removed) {
            Map<UUID, StorageUTXO> accountUtxos = accounts.get(utxo.getUTXOOwner());
            if (accountUtxos == null) {
                logger.error("Unable to remove UTXO. " +
                        "Future consistency cannot be ensured.");
            } else {
                accountUtxos.remove(utxo.getId());
                if (accountUtxos.isEmpty())
                    accounts.remove(utxo.getUTXOOwner());
            }
        }
    }
}
