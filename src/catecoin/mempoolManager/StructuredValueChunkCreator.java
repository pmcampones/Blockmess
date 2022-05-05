package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.Transaction;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilResistantElectionProof;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class StructuredValueChunkCreator {

    public MempoolChunk createChunk(LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> block, int cumulativeWeight) {
        List<Transaction> unwrappedContent = block.getContentList()
                .getContentList()
                .stream()
                .map(StructuredValue::getInnerValue)
                .collect(toList());
        ContentList<Transaction> newContentList = new ContentList<>(unwrappedContent);
        LedgerBlock<ContentList<Transaction>, SybilResistantElectionProof> innerBlock = new LedgerBlockImp<>(block.getBlockId(),
                block.getInherentWeight(), block.getPrevRefs(), newContentList,
                block.getSybilElectionProof(), block.getSignatures());
        return new MinimalistChunkCreator().createChunk(innerBlock, cumulativeWeight);
    }
}
