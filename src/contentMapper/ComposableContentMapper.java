package contentMapper;

import cmux.CMuxMask;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public interface ComposableContentMapper
        extends ContentMapper {

    Pair<ComposableContentMapper, ComposableContentMapper> separateContent(
            CMuxMask mask, ContentMapper innerLft, ContentMapper innerRgt);

    void aggregateContent(Collection<ComposableContentMapper> blockConstructors);

}
