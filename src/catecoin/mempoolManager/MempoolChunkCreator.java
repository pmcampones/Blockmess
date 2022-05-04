package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilElectionProof;

public interface MempoolChunkCreator<E extends IndexableContent, P extends SybilElectionProof> {

    MempoolChunk createChunk(LedgerBlock<BlockContent<E>, P> block, int cumulativeWeight);

}
