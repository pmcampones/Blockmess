package demo.ycsb;


import applicationInterface.ApplicationInterface;
import demo.ycsb.pojos.DeleteRequest;
import demo.ycsb.pojos.PostRequest;
import demo.ycsb.pojos.ReadRequest;
import demo.ycsb.pojos.ScanRequest;
import demo.ycsb.serializers.GenericPojoSerializer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.TreeMap;

public class OperationProcessor extends ApplicationInterface {

    private final TreeMap<String, Table> tables = new TreeMap<>();

    public OperationProcessor(@NotNull String[] blockmessProperties) {
        super(blockmessProperties);
    }

    public byte[] invokeOp(byte[] operation) {
        //return super.invokeSyncOperation(operation).getLeft();
        return processOperation(operation);
    }

    @Override
    public byte[] processOperation(byte[] operation) {
        try (var in = new ByteArrayInputStream(operation); var oin = new ObjectInputStream(in)) {
            byte opIdx = oin.readByte();
            if (opIdx < 0 || opIdx > DBClient.OP.values().length)
                return new byte[0];
            DBClient.OP op = DBClient.OP.values()[opIdx];
            switch (op) {
                case READ:
                    return processReadRequest(oin);
                case SCAN:
                    return processScanRequest(oin);
                case UPDATE:
                    return processUpdateRequest(oin);
                case INSERT:
                    return processInsert(oin);
                case DELETE:
                    return processDelete(oin);
                default:
                    return new byte[0];
            }
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private byte[] processDelete(ObjectInputStream oin) throws IOException {
        DeleteRequest delete = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(delete.getTable());
        if (table == null)
            return new byte[0];
        var record = table.deleteRecord(delete.getKey());
        return record.map(stringMap -> GenericPojoSerializer.serializePojo((Serializable) stringMap))
                .orElseGet(() -> new byte[0]);
    }

    private byte[] processInsert(ObjectInputStream oin) throws IOException {
        PostRequest insert = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(insert.getTable());
        if (table == null) {
            table = new Table();
            tables.put(insert.getTable(), table);
        }
        var record = table.insertRecord(insert.getKey(), insert.getFields());
        return record.map(stringMap -> GenericPojoSerializer.serializePojo((Serializable) stringMap))
                .orElseGet(() -> new byte[0]);
    }

    private byte[] processUpdateRequest(ObjectInputStream oin) throws IOException {
        PostRequest update = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(update.getTable());
        if (table == null)
            return new byte[0];
        var record = table.updateRecord(update.getKey(), update.getFields());
        return record.map(stringMap -> GenericPojoSerializer.serializePojo((Serializable) stringMap))
                .orElseGet(() -> new byte[0]);
    }

    private byte[] processScanRequest(ObjectInputStream oin) throws IOException {
        ScanRequest scan = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(scan.getTable());
        if (table == null)
            return new byte[0];
        var record = table.scanRecords(scan.getStartKey(), scan.getRecordCount(), scan.getFields());
        if (!(record instanceof Serializable))
            record = new ArrayList<>(record);
        return GenericPojoSerializer.serializePojo((Serializable) record);
    }

    private byte[] processReadRequest(ObjectInputStream oin) throws IOException {
        ReadRequest read = GenericPojoSerializer.deserialize(oin);
        var table = tables.get(read.getTable());
        if (table == null)
            return new byte[0];
        var record = table.readRecord(read.getKey(), read.getFields());
        return record.map(stringMap -> GenericPojoSerializer.serializePojo((Serializable) stringMap))
                .orElseGet(() -> new byte[0]);
    }


}
