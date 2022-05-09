package ledger;

import applicationInterface.OperationToCMuxIdentifierMapper;
import blockConstructors.CMuxMask;
import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
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
            out.writeShort(appContent.getSerializedSize());
            out.writeBytes(appContent.content);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) {
            byte[] content = new byte[in.readShort()];
            in.readBytes(content);
            OperationToCMuxIdentifierMapper mapper = OperationToCMuxIdentifierMapper.getSingleton();
            return new AppContent(content, mapper.mapToCmuxId1(content), mapper.mapToCmuxId2(content));
        }
    };
    private final transient UUID id;
    private transient final byte[] hashVal, cmuxId1, cmuxId2;
    private final transient CMuxMask mask = new CMuxMask();
    private final byte[] content;

    public AppContent(byte[] content, byte[] cmuxId1, byte[] cmuxId2) {
        super(ID);
        this.hashVal = CryptographicUtils.hashInput(content);
        this.id = CryptographicUtils.generateUUIDFromBytes(hashVal);
        this.cmuxId1 = cmuxId1;
        this.cmuxId2 = cmuxId2;
        this.content = content;
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
        return content.length;
    }
}
