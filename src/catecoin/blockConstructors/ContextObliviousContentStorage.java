package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;

import java.io.IOException;
import java.util.*;

public class ContextObliviousContentStorage<E extends IndexableContent> extends AbstractContentStorage<E> {

    private final Properties props;

    private final MempoolManager<E,?> mempoolManager;

    public ContextObliviousContentStorage(Properties props, MempoolManager<E,?> mempoolManager) {
        super(props, mempoolManager);
        this.props = props;
        this.mempoolManager = mempoolManager;
    }

    @Override
    public List<E> generateBlockContentList(Collection<UUID> states, int usedSpace) throws IOException {
        return super.getContentList(usedSpace, Collections.emptySet());
    }

    @Override
    public List<E> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs) throws IOException {
        return super.getContentWithinBounds(usedSpace, Collections.emptySet(), maxTxs);
    }

    @Override
    public PrototypicalContentStorage<E> clonePrototype() {
        return new ContextObliviousContentStorage<>(props, mempoolManager);
    }
}
