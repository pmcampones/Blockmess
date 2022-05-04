package catecoin.replies;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import sybilResistantElection.SybilElectionProof;
import utils.IDGenerator;

public class BlockContentReply<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    private final B blockProposal;

    public BlockContentReply(B blockProposal) {
        super(ID);
        this.blockProposal = blockProposal;
    }

    public B getBlockProposal() {
        return blockProposal;
    }
}
