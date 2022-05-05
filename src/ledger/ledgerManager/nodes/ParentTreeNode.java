package ledger.ledgerManager.nodes;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ParentTreeNode {

    void replaceChild(BlockmessChain newChild);

    void forgetUnconfirmedChains(Set<UUID> discartedChainsIds);

    void createChains(List<BlockmessChain> createdChains);

    ParentTreeNode getTreeRoot();

}
