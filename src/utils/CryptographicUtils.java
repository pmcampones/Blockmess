package utils;

import applicationInterface.GlobalProperties;
import io.netty.buffer.ByteBuf;
import lombok.SneakyThrows;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Properties;
import java.util.UUID;

public class CryptographicUtils {

	/**
	 * Name of the algorithm to be used when creating Signature instances.
	 * <p>Failure to use a single unified algorithm for verification of
	 * signatures will lead to signatures being unduly invalid.</p>
	 * <p>Note: The ECDSA keys and signatures do not have constant size, unlike the more traditional RSA variant.
	 * This fact must be taken into consideration when serializing objects.</p>
	 */
	public static final String SIGN_ALGORITHM = "SHA256withECDSA";

	/**
	 * Name of the algorithm to be used when creating MessageDigest instances.
	 */
	public static final String HASH_ALGORITHM = "SHA-256";

	/**
	 * Common operation used in the serializers.
	 * <p>Serializes an asymmetric cryptographic key.</p>
	 *
	 * @param key The key to be serialized. The type of key has been left open, but a PrivateKey should never be sent.
	 */
	public static void serializeKey(Key key, ByteBuf out) {
		byte[] encoded = key.getEncoded();
		out.writeShort(encoded.length);
		out.writeBytes(encoded);
	}

	/**
	 * Computes the size occupied by an asymmetric key during serialization.
	 *
	 * @param key The key being serialized.
	 * @return Number of bytes occupied in the serialization.
	 */
	public static int computeKeySize(Key key) {
		return Short.BYTES + key.getEncoded().length;
	}

	/**
	 * Common operation used in the serializers. Serializes an asymmetric public key.
	 *
	 * @return The public key whose contents are found at the current location of the byte buffer received. Or null if a
	 * problem is found in the deserialization process.
	 */
	@SneakyThrows
	public static PublicKey deserializePubKey(ByteBuf in) {
		byte[] encoded = new byte[in.readShort()];
		in.readBytes(encoded);
		return fromEncodedFormat(encoded);
	}

	public static PublicKey fromEncodedFormat(byte[] encoded)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(encoded);
		KeyFactory factory = KeyFactory.getInstance("EC");
		return factory.generatePublic(pubKeySpec);
	}

	/**
	 * Computes a UUID from the input received by a pojo. Ideally this input is a deterministic function of the pojo's
	 * parameters
	 *
	 * @param input The contents of the pojo without being hashed. The correctness of the program is maintained if the
	 *              input received is hashed, but it's a waste.
	 * @return A unique identifier for the instance calling the method.
	 */
	@SneakyThrows
	public static UUID generateUUIDFromBytes(byte[] input) {
		byte[] hashedContent = MessageDigest.getInstance(HASH_ALGORITHM).digest(input);
		try (var in = new DataInputStream(new ByteArrayInputStream(hashedContent))) {
			return new UUID(in.readLong(), in.readLong());
		}
	}

	@SneakyThrows
	public static byte[] hashInput(byte[] input) {
		return MessageDigest.getInstance(HASH_ALGORITHM).digest(input);
	}

	@SneakyThrows
	public static byte[] signUUID(PrivateKey signer, UUID id) {
		Signature signature = Signature.getInstance(CryptographicUtils.SIGN_ALGORITHM);
		signature.initSign(signer);
		byte[] idBytes = getIDBytes(id);
		signature.update(idBytes);
		return signature.sign();
	}

	public static byte[] getIDBytes(UUID id) {
		ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES);
		buffer.putLong(id.getMostSignificantBits());
		buffer.putLong(id.getLeastSignificantBits());
		return buffer.array();
	}

	/**
	 * Signs a BroadcastValue.
	 *
	 * @param byteFields The contents to be signed in a byte array format.
	 * @param signer     The private key of the object issuer. Used to sign the transaction.
	 * @return The signature of the Pojo's contents.
	 */
	@SneakyThrows
	public static byte[] getFieldsSignature(byte[] byteFields, PrivateKey signer) {
		Signature signature = Signature.getInstance(SIGN_ALGORITHM);
		signature.initSign(signer);
		signature.update(byteFields);
		return signature.sign();
	}

	/**
	 * Verifies the signature of a pojo.
	 *
	 * @param signedContent The contents of the pojo as signed by its issuer.
	 * @param byteFields    The content of the pojo as a byte array.
	 * @param verifier      Public key matching the key used to sign the pojo.
	 * @return True if the signedContent matches the byteFields with the received verifier.
	 */
	public static boolean verifyPojoSignature(byte[] signedContent, byte[] byteFields, PublicKey verifier) throws InvalidKeyException, SignatureException {
		try {
			Signature signature = Signature.getInstance(SIGN_ALGORITHM);
			signature.initVerify(verifier);
			signature.update(byteFields);
			return signature.verify(signedContent);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new Error(); //Should never happen, and if it does, it's the node computing the method's fault.
		}
	}

	public static KeyPair getNodeKeys() {
		Properties props = GlobalProperties.getProps();
		boolean generateKeys = props.getProperty("generateKeys", "T").equals("T");
		return generateKeys ? generateECDSAKeyPair() : readECDSAKeyPair();
	}

	@SneakyThrows
	public static KeyPair generateECDSAKeyPair() {
		ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		byte[] seed = String.valueOf(System.nanoTime()).getBytes();
		g.initialize(ecSpec, new SecureRandom(seed));
		return g.generateKeyPair();
	}

	public static KeyPair readECDSAKeyPair() {
		Properties props = GlobalProperties.getProps();
		PublicKey pub = readECDSAPublicKey(props.getProperty("myPublic"));
		PrivateKey sec = readECDSASecretKey();
		return new KeyPair(pub, sec);
	}

	@SneakyThrows
	public static PublicKey readECDSAPublicKey(String fileLocation) {
		try (FileReader keyReader = new FileReader(fileLocation);
			 PemReader pemReader = new PemReader(keyReader)) {
			KeyFactory factory = KeyFactory.getInstance("EC");
			PemObject pemObject = pemReader.readPemObject();
			byte[] content = pemObject.getContent();
			X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
			return factory.generatePublic(pubKeySpec);
		}
	}

	@SneakyThrows
	public static PrivateKey readECDSASecretKey() {
		Properties props = GlobalProperties.getProps();
		String fileLocation = props.getProperty("mySecret");
		try (FileReader keyReader = new FileReader(fileLocation);
			 PemReader pemReader = new PemReader(keyReader)) {
			KeyFactory factory = KeyFactory.getInstance("EC");
			PemObject pemObject = pemReader.readPemObject();
			byte[] content = pemObject.getContent();
			PKCS8EncodedKeySpec secretKeySpec = new PKCS8EncodedKeySpec(content);
			return factory.generatePrivate(secretKeySpec);
		}
	}
}
