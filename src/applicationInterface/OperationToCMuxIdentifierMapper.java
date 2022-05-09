package applicationInterface;

public interface OperationToCMuxIdentifierMapper {

    byte[] mapToCmuxId1(byte[] operation);

    byte[] mapToCmuxId2(byte[] operation);
}
