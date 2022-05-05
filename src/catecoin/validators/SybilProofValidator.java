package catecoin.validators;

import ledger.blocks.SizeAccountable;
import main.ProtoPojo;

import java.security.PublicKey;

public interface SybilProofValidator<P extends ProtoPojo & SizeAccountable> {

    boolean isValid(P proof, PublicKey proposer);

}
