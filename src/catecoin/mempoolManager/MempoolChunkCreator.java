package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilElectionProof;

public interface MempoolChunkCreator<E extends IndexableContent, P extends SybilElectionProof> {

    MempoolChunk createChunk(LedgerBlock<ContentList<E>, P> block, int cumulativeWeight);

}
