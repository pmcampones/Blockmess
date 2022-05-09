package blockConstructors;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public interface ComposableContentStorage
        extends ContentStorage {

    Pair<ComposableContentStorage, ComposableContentStorage> separateContent(
            CMuxMask mask, ContentStorage innerLft, ContentStorage innerRgt);

    void aggregateContent(Collection<ComposableContentStorage> blockConstructors);

}
