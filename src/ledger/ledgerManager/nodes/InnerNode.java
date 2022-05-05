package ledger.ledgerManager.nodes;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilResistantElectionProof;

public interface InnerNode<E extends IndexableContent, C extends ContentList<StructuredValue<E>>, P extends SybilResistantElectionProof>
        extends BlockmessChain<E>, ParentTreeNode<E,C,P> {}
