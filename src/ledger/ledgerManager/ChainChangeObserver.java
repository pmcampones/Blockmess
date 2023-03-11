package ledger.ledgerManager;

/**
 * @author elcampones
 */
public interface ChainChangeObserver {

	void notifyChangesChains(int updatedNumChains);

}
