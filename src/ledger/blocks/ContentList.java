package ledger.blocks;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import cmux.AppContent;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.merkleTree.MerkleRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContentList extends BroadcastValueAbstract {

    public static final short ID = 3920;

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue broadcastValue, ByteBuf out) throws IOException {
            ContentList ContentListList = (ContentList) broadcastValue;
            out.writeInt(ContentListList.contentList.size());
            for (AppContent elem : ContentListList.contentList)
                serializeElement(elem, out);
        }

        private void serializeElement(AppContent elem, ByteBuf out) throws IOException {
            out.writeShort(elem.getClassId());
            elem.getSerializer().serialize(elem, out);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) throws IOException {
            int numElems = in.readInt();
            List<AppContent> contentLst = new ArrayList<>(numElems);
            for (int i = 0; i < numElems; i++)
                contentLst.add(deserializeElem(in));
            return new ContentList(contentLst);
        }

        private AppContent deserializeElem(ByteBuf in) throws IOException {
            short elemClass = in.readShort();
            ISerializer<BroadcastValue> serializer = BroadcastValue.pojoSerializers.get(elemClass);
            return (AppContent) serializer.deserialize(in);
        }
    };

    @Getter
    private final List<AppContent> contentList;

    public ContentList(List<AppContent> contentList) {
        super(ID);
        this.contentList = contentList;
    }

    public byte[] getContentHash() {
        List<byte[]> contentHashes = contentList.stream()
                .map(AppContent::getHashVal)
                .collect(Collectors.toList());
        return new MerkleRoot(contentHashes).getHashValue();
    }

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }

    public int getSerializedSize() throws IOException {
        int accum = 0;
        for (AppContent elem : contentList)
            accum += elem.getSerializedSize();
        return accum;
    }

}
