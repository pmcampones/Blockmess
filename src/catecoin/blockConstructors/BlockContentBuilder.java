package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;

import java.util.List;

/**
 * Builds the {@link BlockContent} component of a {@link ledger.blocks.LedgerBlock} or extensions of it.
 * <p>Follows the builder design pattern.</p>
 * @param <E> Elements in the content batch to be placed in the block.
 * @param <C> Type of block content where the elements E will be placed.
 */
public interface BlockContentBuilder<E extends IndexableContent, C extends BlockContent<E>> {

    C buildContent(List<E> elems);

}
