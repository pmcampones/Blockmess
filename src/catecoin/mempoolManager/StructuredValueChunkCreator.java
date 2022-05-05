package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.blocks.SizeAccountable;
import ledger.ledgerManager.StructuredValue;
import main.ProtoPojo;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class StructuredValueChunkCreator<E extends IndexableContent, P extends ProtoPojo & SizeAccountable>
        implements MempoolChunkCreator<StructuredValue<E>,P> {

    private final MempoolChunkCreator<E,P> inner;

    public StructuredValueChunkCreator(MempoolChunkCreator<E, P> inner) {
        this.inner = inner;
    }

    @Override
    public MempoolChunk createChunk(LedgerBlock<ContentList<StructuredValue<E>>, P> block, int cumulativeWeight) {
        List<E> unwrappedContent = block.getContentList()
                .getContentList()
                .stream()
                .map(StructuredValue::getInnerValue)
                .collect(toList());
        ContentList<E> newContentList = new ContentList<>(unwrappedContent);
        LedgerBlock<ContentList<E>, P> innerBlock = new LedgerBlockImp<>(block.getBlockId(),
                block.getInherentWeight(), block.getPrevRefs(), newContentList,
                block.getSybilElectionProof(), block.getSignatures());
        return inner.createChunk(innerBlock, cumulativeWeight);
    }
}
