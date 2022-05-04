package catecoin.posSpecific.keyBlockManagers;

import java.security.PublicKey;
import java.util.UUID;

public interface KeyBlockManager {

    UUID getHeaviestKeyBlock();

    int getBlockWeight(UUID keyBlock) throws KeyBlockDoesNotExistException;

    PublicKey getBlockProposer(UUID keyBlock) throws KeyBlockDoesNotExistException;

    UUID getPrevReference(UUID keyBlock) throws KeyBlockDoesNotExistException;
}
