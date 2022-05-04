package catecoin.txs;

import ledger.blocks.SizeAccountable;
import main.ProtoPojo;

import java.util.UUID;

public interface IndexableContent extends ProtoPojo, SizeAccountable {

    UUID getId();

    byte[] getHashVal();

    boolean hasValidSemantics();
}
