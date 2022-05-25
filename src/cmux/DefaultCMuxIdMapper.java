package cmux;

import utils.CryptographicUtils;

public class DefaultCMuxIdMapper implements CMuxIdMapper {

    @Override
    public byte[] mapToCmuxId1(byte[] operation) {
        return CryptographicUtils.hashInput(operation);
    }

    @Override
    public byte[] mapToCmuxId2(byte[] operation) {
        byte[] hash = CryptographicUtils.hashInput(operation);
        byte[] seed = new byte[hash.length];
        seed[0] = hash[hash.length - 1];
        System.arraycopy(hash, 0, seed, 1, hash.length - 1);
        return seed;
    }

}
