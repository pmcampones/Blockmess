package catecoin.blocks;

import ledger.blocks.SizeAccountable;

import java.security.PublicKey;
import java.util.UUID;

public interface ValidatorSignature extends SizeAccountable {

    PublicKey getValidatorKey();

    byte[] getValidatorSignature();

    boolean isValid(UUID blockId);

}
