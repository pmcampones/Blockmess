package cmux;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class OperationMetadata {
    public final UUID id;
    public final byte[] cmuxId1, cmuxId2, replicaMetadata;
}
