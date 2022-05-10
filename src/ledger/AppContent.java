package ledger;

import applicationInterface.FixedCMuxIdentifierMapper;
import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import contentStorage.CMuxMask;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.CryptographicUtils;

import java.util.UUID;

public class AppContent extends BroadcastValueAbstract implements BroadcastValue {

    public static final short ID = 1982;

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue broadcastValue, ByteBuf out) {
            AppContent appContent = (AppContent) broadcastValue;
            out.writeInt(appContent.content.length);
            out.writeBytes(appContent.content);
            out.writeShort(appContent.replicaMetadata.length);
            out.writeBytes(appContent.replicaMetadata);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) {
            byte[] content = new byte[in.readInt()];
            in.readBytes(content);
            byte[] replicaMetadata = new byte[in.readShort()];
            in.readBytes(replicaMetadata);
            FixedCMuxIdentifierMapper mapper = FixedCMuxIdentifierMapper.getSingleton();
            return new AppContent(content, mapper.mapToCmuxId1(content), mapper.mapToCmuxId2(content), replicaMetadata);
        }
    };
    private final transient UUID id;
    private transient final byte[] hashVal, cmuxId1, cmuxId2;
    private final transient CMuxMask mask = new CMuxMask();
    private final byte[] content, replicaMetadata;

    public AppContent(byte[] content, byte[] cmuxId1, byte[] cmuxId2, byte[] replicaMetadata) {
        super(ID);
        byte[] fullOperation = concatenate(content, replicaMetadata);
        this.hashVal = CryptographicUtils.hashInput(fullOperation);
        this.id = CryptographicUtils.generateUUIDFromBytes(hashVal);
        this.cmuxId1 = cmuxId1;
        this.cmuxId2 = cmuxId2;
        this.content = content;
        this.replicaMetadata = replicaMetadata;
    }

    private byte[] concatenate(byte[] head, byte[] tail) {
        byte[] res = new byte[head.length + tail.length];
        System.arraycopy(head, 0, res, 0, head.length);
        System.arraycopy(tail, 0, res, head.length, tail.length);
        return res;
    }

    public UUID getId() {
        return id;
    }

    public byte[] getHashVal() {
        return hashVal;
    }

    public boolean hasValidSemantics() {
        return true;
    }

    public byte[] getCmuxId1() {
        return cmuxId1;
    }

    public byte[] getCmuxId2() {
        return cmuxId2;
    }

    public byte[] getContent() {
        return content;
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
