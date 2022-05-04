package catecoin.posSpecific.accountManagers;

import java.security.PublicKey;
import java.util.UUID;

public interface AccountManager {

    int getCirculationCoins();

    int getProposerCoins(PublicKey node, UUID block);

}
