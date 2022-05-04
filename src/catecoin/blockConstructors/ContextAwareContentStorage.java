package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;
import sybilResistantElection.SybilElectionProof;

import java.io.IOException;
import java.util.*;

public class ContextAwareContentStorage<E extends IndexableContent, P extends SybilElectionProof>
        extends AbstractContentStorage<E>  {

    private final MempoolManager<E,P> mempoolManager;

    private final Properties props;

    public ContextAwareContentStorage(Properties props, MempoolManager<E,P> mempoolManager) {
        super(props, mempoolManager);
        this.props = props;
        this.mempoolManager = mempoolManager;
    }

    @Override
    public List<E> generateBlockContentList(Collection<UUID> states, int usedSpace) throws IOException {
        Set<UUID> used = findUsedTransactions(states);
        return getContentList(usedSpace, used);
    }

    @Override
    public List<E> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs) throws IOException {
        Set<UUID> used = findUsedTransactions(states);
        return getContentWithinBounds(usedSpace, used, maxTxs);
    }

    private Set<UUID> findUsedTransactions(Collection<UUID> states) {
        Set<UUID> used = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID state : states)
            used.addAll(mempoolManager.getInvalidTxsFromChunk(state, visited));
        return used;
    }

    @Override
    public PrototypicalContentStorage<E> clonePrototype() {
        return new ContextAwareContentStorage<>(props, mempoolManager);
    }
}
