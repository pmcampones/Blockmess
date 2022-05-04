package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.ledgerManager.StructuredValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public interface ComposableContentStorage<E extends IndexableContent>
        extends ContentStorage<StructuredValue<E>> {

    Pair<ComposableContentStorage<E>, ComposableContentStorage<E>> separateContent(
            StructuredValueMask mask,
            ContentStorage<StructuredValue<E>> innerLft,
            ContentStorage<StructuredValue<E>> innerRgt);

    void aggregateContent(Collection<ComposableContentStorage<E>> blockConstructors);

}
