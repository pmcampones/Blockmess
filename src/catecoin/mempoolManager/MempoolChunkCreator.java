package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.SizeAccountable;
import main.ProtoPojo;

public interface MempoolChunkCreator<E extends IndexableContent, P extends ProtoPojo & SizeAccountable> {

    MempoolChunk createChunk(LedgerBlock<ContentList<E>, P> block, int cumulativeWeight);

}
