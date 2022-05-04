package sybilResistantCommitteeElection.notifications;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import sybilResistantCommitteeElection.SybilElectionProof;
import utils.IDGenerator;

public class IWasElectedWithBlockNotification<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final B blockProposal;

    public IWasElectedWithBlockNotification(B blockProposal) {
        super(ID);
        this.blockProposal = blockProposal;
    }

    public B getBlockProposal() {
        return blockProposal;
    }
}
