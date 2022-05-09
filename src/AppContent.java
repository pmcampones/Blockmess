import java.util.function.Function;

public class AppContent {

    private final byte[] uid, cmuxId1, cmuxId2, content;

    public AppContent(byte[] content, Function<byte[], byte[]> uidMapper,
                      Function<byte[], byte[]> cmuxMapper1, Function<byte[], byte[]> cmuxMapper2) {
        this.content = content;
        this.uid = uidMapper.apply(content);
        this.cmuxId1 = cmuxMapper1.apply(content);
        this.cmuxId2 = cmuxMapper2.apply(content);
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
}
