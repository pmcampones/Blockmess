package ledger.blocks;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import cmux.AppOperation;
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
            for (AppOperation elem : ContentListList.contentList)
                serializeElement(elem, out);
        }

        private void serializeElement(AppOperation elem, ByteBuf out) throws IOException {
            out.writeShort(elem.getClassId());
            elem.getSerializer().serialize(elem, out);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) throws IOException {
            int numElems = in.readInt();
            List<AppOperation> contentLst = new ArrayList<>(numElems);
            for (int i = 0; i < numElems; i++)
                contentLst.add(deserializeElem(in));
            return new ContentList(contentLst);
        }

        private AppOperation deserializeElem(ByteBuf in) throws IOException {
            short elemClass = in.readShort();
            ISerializer<BroadcastValue> serializer = BroadcastValue.pojoSerializers.get(elemClass);
            return (AppOperation) serializer.deserialize(in);
        }
    };

    @Getter
    private final List<AppOperation> contentList;

    public ContentList(List<AppOperation> contentList) {
        super(ID);
        this.contentList = contentList;
    }

    public byte[] getContentHash() {
        List<byte[]> contentHashes = contentList.stream()
                .map(AppOperation::getHashVal)
                .collect(Collectors.toList());
        return new MerkleRoot(contentHashes).getHashValue();
    }

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }

    public int getSerializedSize() {
        int accum = 0;
        for (AppOperation elem : contentList)
            accum += elem.getSerializedSize();
        return accum;
    }

}
