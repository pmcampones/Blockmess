package cmux;

public class CMuxMask {

    public enum MaskResult {
        LEFT,
        RIGHT,
        CENTER
    }

    private int byteIndex = 0;

    private byte byteMask = 7;

    public CMuxMask() {}

    public CMuxMask(int startBit) {
        this.byteIndex = startBit / Byte.SIZE;
        this.byteMask = (byte) ((Byte.SIZE - 1) - startBit % Byte.SIZE);
    }

    public MaskResult matchIds(byte[] id1, byte[] id2) {
        int match1 = computeMatchVal(id1);
        int match2 = computeMatchVal(id2);
        if (match1 != match2)
            return MaskResult.CENTER;
        else if (match1 == 0)
            return MaskResult.LEFT;
        else if (match1 == 1)
            return MaskResult.RIGHT;
        else {
            System.err.println("I don't know why this isn't working.");
            System.exit(1);
            return null;
        }
    }

    private int computeMatchVal(byte[] id) {
        return (id[byteIndex] & (1 << byteMask)) >> byteMask;
    }

    public void advanceMask() {
        if (byteMask > 0)
            byteMask--;
        else {
            byteIndex++;
            byteMask = 7;
        }
    }

}
