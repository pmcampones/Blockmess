package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContentStorage<E extends IndexableContent> {

    List<E> generateContentListList(Collection<UUID> states, int usedSpace) throws IOException;

    List<E> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs) throws IOException;

    void submitContent(Collection<E> content);

    void submitContent(E content);

    void deleteContent(Set<UUID> contentIds);

    Collection<E> getStoredContent();

    //THESE LOWER TWO ARE ONLY USED FOR TESTS: REMOVE WHEN DOING HIGH LOAD TESTS

    void halveChainThroughput();

    void doubleChainThroughput();

    int getThroughputReduction();

    void setChainThroughputReduction(int reduction);

}
