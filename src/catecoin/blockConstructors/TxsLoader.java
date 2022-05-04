package catecoin.blockConstructors;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;

public interface TxsLoader<E> {

    void loadTxs(Collection<E> txs);

}
