import catecoin.blockConstructors.CMuxMask;

import java.util.function.Function;

public class AppContent {

    private final byte[] uid, cmuxId1, cmuxId2, content;

    private final CMuxMask mask = new CMuxMask();

    public AppContent(byte[] content, Function<byte[], byte[]> uidMapper,
                      Function<byte[], byte[]> cmuxMapper1, Function<byte[], byte[]> cmuxMapper2) {
        this.content = content;
        this.uid = uidMapper.apply(content);
        this.cmuxId1 = cmuxMapper1.apply(content);
        this.cmuxId2 = cmuxMapper2.apply(content);
    }

    public byte[] getUid() {
        return uid;
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
}
