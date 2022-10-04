package demo.cryptocurrency;

import cmux.CMuxIdMapper;

public class CryptocurrencyCMuxMapper implements CMuxIdMapper {

	@Override
	public byte[] mapToCmuxId1(byte[] operation) {
		return new byte[0];
	}

	@Override
	public byte[] mapToCmuxId2(byte[] operation) {
		return new byte[0];
	}

}
