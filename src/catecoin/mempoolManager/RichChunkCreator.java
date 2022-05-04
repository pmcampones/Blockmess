package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.blocks.chunks.RichMempoolChunk;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;

public class RichChunkCreator implements MempoolChunkCreator<SlimTransaction,SortitionProof> {

    @Override
    public MempoolChunk createChunk(LedgerBlock<BlockContent<SlimTransaction>, SortitionProof> block, int cumulativeWeight) {
        MempoolChunk chunk = new MinimalistChunkCreator<SortitionProof>().createChunk(block, cumulativeWeight);
        MinimalistMempoolChunk innerChunk = (MinimalistMempoolChunk) chunk;
        return new RichMempoolChunk(innerChunk, cumulativeWeight, block.getProposer(), block.getSybilElectionProof());
    }

}
