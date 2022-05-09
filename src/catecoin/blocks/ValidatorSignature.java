package catecoin.blocks;

import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.CryptographicUtils;

import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.UUID;

public class ValidatorSignature {

    public final static ISerializer<ValidatorSignature> serializer = new ISerializer<>() {

        @Override
        public void serialize(ValidatorSignature validatorSignature, ByteBuf out) {
            CryptographicUtils.serializeKey(validatorSignature.getValidatorKey(), out);
            out.writeShort(validatorSignature.getValidatorSignature().length);
            out.writeBytes(validatorSignature.getValidatorSignature());
        }

        @Override
        public ValidatorSignature deserialize(ByteBuf in) throws IOException {
            PublicKey validator = CryptographicUtils.deserializePubKey(in);
            byte[] signedContent = new byte[in.readShort()];
            in.readBytes(signedContent);
            return new ValidatorSignature(validator, signedContent);
        }
    };

    private final PublicKey validator;
    private static final Logger logger = LogManager.getLogger(ValidatorSignature.class);
    private final byte[] signedContent;

    public ValidatorSignature(KeyPair validator, UUID blockId)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        this.validator = validator.getPublic();
        this.signedContent = CryptographicUtils.signUUID(validator.getPrivate(), blockId);
    }

    public PublicKey getValidatorKey() {
        return validator;
    }

    public byte[] getValidatorSignature() {
        return signedContent;
    }

    public boolean isValid(UUID blockId) {
        try {
            return CryptographicUtils.verifyUUIDSignatur(validator, blockId, signedContent);
        } catch (Exception e) {
            logger.info("Unable to validate signature because of exception: '{}'", e.getMessage());
        }
        return false;
    }

    public int getSerializedSize() throws IOException {
        return CryptographicUtils.computeKeySize(validator) + Short.BYTES + signedContent.length;
    }

    private ValidatorSignature(PublicKey validator, byte[] signedContent) {
        this.validator = validator;
        this.signedContent = signedContent;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ValidatorSignature))
            return false;
        ValidatorSignature other = (ValidatorSignature) obj;
        return this.validator.equals(other.validator)
                && Arrays.equals(this.signedContent, other.signedContent);
    }
}
