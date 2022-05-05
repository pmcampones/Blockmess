package ledger.ledgerManager.nodes;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilElectionProof;

public interface InnerNode<E extends IndexableContent, C extends ContentList<StructuredValue<E>>, P extends SybilElectionProof>
        extends BlockmessChain<E,C,P>, ParentTreeNode<E,C,P> {}
