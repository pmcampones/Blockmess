package catecoin.blocks;

import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.UUID;

public class ValidatorSignatureImp implements AdversarialValidatorSignature {

    private static final Logger logger = LogManager.getLogger(ValidatorSignatureImp.class);

    private final PublicKey validator;

    private byte[] signedContent;

    public ValidatorSignatureImp(KeyPair validator, UUID blockId)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        this.validator = validator.getPublic();
        this.signedContent = CryptographicUtils.signUUID(validator.getPrivate(), blockId);
    }

    private ValidatorSignatureImp(PublicKey validator, byte[] signedContent) {
        this.validator = validator;
        this.signedContent = signedContent;
    }

    @Override
    public PublicKey getValidatorKey() {
        return validator;
    }

    @Override
    public byte[] getValidatorSignature() {
        return signedContent;
    }

    @Override
    public boolean isValid(UUID blockId) {
        try {
            return CryptographicUtils.verifyUUIDSignatur(validator, blockId, signedContent);
        } catch (Exception e) {
            logger.info("Unable to validate signature because of exception: '{}'", e.getMessage());
        }
        return false;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return CryptographicUtils.computeKeySize(validator) + Short.BYTES + signedContent.length;
    }

    @Override
    public void forgeSignature(byte[] forgedSignature) {
        this.signedContent = forgedSignature;
    }

    public final static ISerializer<AdversarialValidatorSignature> serializer = new ISerializer<>() {

        @Override
        public void serialize(AdversarialValidatorSignature validatorSignature, ByteBuf out) {
            CryptographicUtils.serializeKey(validatorSignature.getValidatorKey(), out);
            out.writeShort(validatorSignature.getValidatorSignature().length);
            out.writeBytes(validatorSignature.getValidatorSignature());
        }

        @Override
        public AdversarialValidatorSignature deserialize(ByteBuf in) throws IOException {
            PublicKey validator = CryptographicUtils.deserializePubKey(in);
            byte[] signedContent = new byte[in.readShort()];
            in.readBytes(signedContent);
            return new ValidatorSignatureImp(validator, signedContent);
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ValidatorSignatureImp))
            return false;
        ValidatorSignatureImp other = (ValidatorSignatureImp) obj;
        return this.validator.equals(other.validator)
                && Arrays.equals(this.signedContent, other.signedContent);
    }
}
