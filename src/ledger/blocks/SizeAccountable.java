package ledger.blocks;

import java.io.IOException;

/**
 * This interface indicates that the object implementing it must be
 * aware of the size (in bytes) it occupies when serialized
 */
public interface SizeAccountable {

    /**
     * @throws IOException In order to compute the serialized size of the object,
     * some inner components may need to be serialized, resulting in this exception.
     */
    int getSerializedSize() throws IOException;

}
