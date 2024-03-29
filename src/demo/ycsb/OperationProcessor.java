package demo.ycsb;

import applicationInterface.ApplicationInterface;
import demo.ycsb.pojos.DeleteRequest;
import demo.ycsb.pojos.PostRequest;
import demo.ycsb.serializers.GenericPojoSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class OperationProcessor extends ApplicationInterface {

    private Map<String, Table> tables = new HashMap<>();

    private static OperationProcessor singleton;

    private OperationProcessor() {
        super(new String[0]);
    }

    public static OperationProcessor getSingleton() {
        if (singleton == null)
            singleton = new OperationProcessor();
        return singleton;
    }

    public byte[] invokeOp(byte[] operation) {
        return super.invokeSyncOperation(operation).getLeft();
        //return processOperation(operation);
    }

    @Override
    public byte[] processOperation(byte[] operation) {
        try (var in = new ByteArrayInputStream(operation); var oin = new ObjectInputStream(in)) {
            byte opIdx = oin.readByte();
            if (opIdx < 0 || opIdx > DBClient.OP.values().length)
                return new byte[] {(byte) DBClient.RETURN_CODES.ERROR.ordinal()};
            DBClient.OP op = DBClient.OP.values()[opIdx];
            switch (op) {
                case UPDATE:
                    return processUpdateRequest(oin);
                case INSERT:
                    return processInsert(oin);
                case DELETE:
                    return processDelete(oin);
                default:
                    return new byte[] {(byte) DBClient.RETURN_CODES.ERROR.ordinal()};
            }
        } catch (IOException e) {
            return new byte[] {(byte) DBClient.RETURN_CODES.ERROR.ordinal()};
        }
    }

    private byte[] processDelete(ObjectInputStream oin) throws IOException {
        DeleteRequest delete = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(delete.getTable());
        if (table == null)
            return new byte[] {(byte) DBClient.RETURN_CODES.NOT_FOUND.ordinal()};
        var record = table.deleteRecord(delete.getKey());
        return record.isPresent() ? new byte[] {(byte) DBClient.RETURN_CODES.OK.ordinal()}
                : new byte[] {(byte) DBClient.RETURN_CODES.NOT_FOUND.ordinal()};
    }

    private byte[] processInsert(ObjectInputStream oin) throws IOException {
        PostRequest insert = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(insert.getTable());
        if (table == null) {
            table = new Table();
            tables.put(insert.getTable(), table);
        }
        table.insertRecord(insert.getKey(), insert.getFields());
        return new byte[]{(byte) DBClient.RETURN_CODES.OK.ordinal()};
    }

    private byte[] processUpdateRequest(ObjectInputStream oin) throws IOException {
        PostRequest update = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(update.getTable());
        if (table == null)
            return new byte[] {(byte) DBClient.RETURN_CODES.NOT_FOUND.ordinal()};
        var record = table.updateRecord(update.getKey(), update.getFields());
        return record.isPresent() ? new byte[] {(byte) DBClient.RETURN_CODES.OK.ordinal()}
                : new byte[] {(byte) DBClient.RETURN_CODES.NOT_FOUND.ordinal()};
    }

    public Optional<List<Map<String,byte[]>>> processScanRequest(String tableKey, String startKey, int recordCount, Set<String> fields) {
        var table = tables.get(tableKey);
        if (table == null)
            return Optional.empty();
        var records = table.scanRecords(startKey, recordCount, fields);
        return records.isEmpty() ? Optional.empty() : Optional.of(records);
    }

    public Optional<Map<String,byte[]>> processReadRequest(String tableKey, String recordKey, Set<String> fields) {
        var table = tables.get(tableKey);
        return table == null ? Optional.empty() : table.readRecord(recordKey, fields);
    }

    public void reset() {
        this.tables = new HashMap<>();
    }

}
