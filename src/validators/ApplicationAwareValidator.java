package validators;

import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author elcampones
 */
public interface ApplicationAwareValidator {

    Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation);

    boolean validateBlockContent(BlockmessBlock block);

}
