package catecoin.posSpecific.keyBlockManagers;

import java.security.PublicKey;
import java.util.UUID;

public interface KeyBlockManager {

    UUID getHeaviestKeyBlock();

}
