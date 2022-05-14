package cmux;

import utils.CryptographicUtils;

public class DefaultCMuxIdMapper implements CMuxIdMapper {

    @Override
    public byte[] mapToCmuxId1(byte[] operation) {
        byte[] hash = CryptographicUtils.hashInput(operation);
        byte[] seed = new byte[hash.length + 1];
        System.arraycopy(hash, 0, seed, 0, hash.length);
        return seed;
    }

    @Override
    public byte[] mapToCmuxId2(byte[] operation) {
        byte[] hash = CryptographicUtils.hashInput(operation);
        byte[] seed = new byte[hash.length + 1];
        System.arraycopy(hash, 0, seed, 1, hash.length);
        return seed;
    }

}
