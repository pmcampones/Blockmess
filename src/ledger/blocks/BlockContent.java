package ledger.blocks;

import main.ProtoPojo;

import java.io.Serializable;
import java.util.List;

public interface BlockContent<E extends ProtoPojo> extends ProtoPojo, Serializable, SizeAccountable {

    /**
     * Verifies if the content of the block is valid.
     */
    boolean hasValidSemantics();

    List<E> getContentList();

    byte[] getContentHash();

}
