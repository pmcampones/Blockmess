package operationMapper;

import cmux.CMuxMask;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public interface ComposableOperationMapper
        extends OperationMapper {

    Pair<ComposableOperationMapper, ComposableOperationMapper> separateOperations(
            CMuxMask mask, OperationMapper innerLft, OperationMapper innerRgt);

    void aggregateOperations(Collection<ComposableOperationMapper> operationMappers);

}
