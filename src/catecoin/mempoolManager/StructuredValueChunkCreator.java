package catecoin.mempoolManager;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.ledgerManager.StructuredValue;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class StructuredValueChunkCreator<E extends IndexableContent, P extends SybilElectionProof>
        implements MempoolChunkCreator<StructuredValue<E>,P> {

    private final MempoolChunkCreator<E,P> inner;

    public StructuredValueChunkCreator(MempoolChunkCreator<E, P> inner) {
        this.inner = inner;
    }

    @Override
    public MempoolChunk createChunk(LedgerBlock<BlockContent<StructuredValue<E>>, P> block, int cumulativeWeight) {
        List<E> unwrappedContent = block.getBlockContent()
                .getContentList()
                .stream()
                .map(StructuredValue::getInnerValue)
                .collect(toList());
        BlockContent<E> newBlockContent = new SimpleBlockContentList<>(unwrappedContent);
        LedgerBlock<BlockContent<E>, P> innerBlock = new LedgerBlockImp<>(block.getBlockId(),
                block.getInherentWeight(), block.getPrevRefs(), newBlockContent,
                block.getSybilElectionProof(), block.getSignatures());
        return inner.createChunk(innerBlock, cumulativeWeight);
    }
}
