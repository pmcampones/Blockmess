package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;

public interface PrototypicalContentStorage<E extends IndexableContent> extends ContentStorage<E> {

    PrototypicalContentStorage<E> clonePrototype();

}
