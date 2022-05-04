package ledger.ledgerManager.nodes;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilElectionProof;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ParentTreeNode<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof> {

    void replaceChild(BlockmessChain<E,C,P> newChild);

    void forgetUnconfirmedChains(Set<UUID> discartedChainsIds);

    void createChains(List<BlockmessChain<E,C,P>> createdChains);

    ParentTreeNode<E,C,P> getTreeRoot();

}
