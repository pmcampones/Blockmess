package ledger.ledgerManager;

/**
 * @author elcampones
 * @project Blockmess_Simple_PoET
 */
public interface ChainChangeObserver {

	void notifyChangesChains(int updatedNumChains);

}
