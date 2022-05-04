package catecoin.blockConstructors;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;

import java.util.List;

/**
 * Builds the blocks with a {@link SimpleBlockContentList},
 * the simplest implementation of {@link BlockContent}.
 */
public class SimpleBlockContentListBuilder<E extends IndexableContent>
        implements BlockContentBuilder<E, BlockContent<E>> {

    @Override
    public BlockContent<E> buildContent(List<E> elems) {
        return new SimpleBlockContentList<>(elems);
    }
}
