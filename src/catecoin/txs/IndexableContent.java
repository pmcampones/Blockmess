package catecoin.txs;

import broadcastProtocols.BroadcastValue;
import ledger.blocks.SizeAccountable;

import java.util.UUID;

public interface IndexableContent extends BroadcastValue, SizeAccountable {

    UUID getId();

    byte[] getHashVal();

    boolean hasValidSemantics();
}
