package cmux;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.CryptographicUtils;

import java.util.UUID;

public class AppOperation extends BroadcastValueAbstract {

	public static final short ID = 1982;

	public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

		@Override
		public void serialize(BroadcastValue broadcastValue, ByteBuf out) {
			AppOperation appOperation = (AppOperation) broadcastValue;
			out.writeInt(appOperation.content.length);
			out.writeBytes(appOperation.content);
			out.writeShort(appOperation.replicaMetadata.length);
			out.writeBytes(appOperation.replicaMetadata);
		}

		@Override
		public BroadcastValue deserialize(ByteBuf in) {
			byte[] content = new byte[in.readInt()];
			in.readBytes(content);
			byte[] replicaMetadata = new byte[in.readShort()];
			in.readBytes(replicaMetadata);
			return new AppOperation(content, replicaMetadata);
		}
	};

	@Getter
	private final transient UUID id;

	@Getter
	private transient final byte[] hashVal, cmuxId1, cmuxId2;
	private final transient CMuxMask mask = new CMuxMask();

	@Getter
	private final byte[] content, replicaMetadata;

	public AppOperation(byte[] content, byte[] replicaMetadata) {
		super(ID);
		byte[] fullOperation = concatenate(content, replicaMetadata);
		this.hashVal = CryptographicUtils.hashInput(fullOperation);
		this.id = CryptographicUtils.generateUUIDFromBytes(hashVal);
		CMuxIdMapper mapper = FixedCMuxIdMapper.getSingleton();
		this.cmuxId1 = mapper.mapToCmuxId1(fullOperation);
		this.cmuxId2 = mapper.mapToCmuxId2(fullOperation);
		this.content = content;
		this.replicaMetadata = replicaMetadata;
	}

	private byte[] concatenate(byte[] head, byte[] tail) {
		byte[] res = new byte[head.length + tail.length];
		System.arraycopy(head, 0, res, 0, head.length);
		System.arraycopy(tail, 0, res, head.length, tail.length);
		return res;
	}

	public CMuxMask.MaskResult matchIds() {
		return mask.matchIds(cmuxId1, cmuxId2);
	}

	public void advanceMask() {
		mask.advanceMask();
	}

	@Override
	public ISerializer<BroadcastValue> getSerializer() {
		return serializer;
	}

	public int getSerializedSize() {
		return content.length + replicaMetadata.length + Integer.BYTES + Short.BYTES;
	}
}
