package demo.ycsb;

import demo.ycsb.pojos.DeleteRequest;
import demo.ycsb.pojos.PostRequest;
import demo.ycsb.serializers.GenericPojoSerializer;
import org.jetbrains.annotations.NotNull;
import site.ycsb.ByteArrayByteIterator;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class DBClient extends DB {

    private final OperationProcessor proxy = OperationProcessor.getSingleton();

    @Override
    public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
        var record = proxy.processReadRequest(table, key, fields);
        record.ifPresent(r -> result.putAll(toByteIterator(r)));
        return record.isPresent() ? Status.OK : Status.NOT_FOUND;
    }

    private static Map<String, ByteIterator> toByteIterator (Map<String, byte[]> og) {
        return og.entrySet().stream()
                .collect(toMap(Map.Entry::getKey,
                        e -> new ByteArrayByteIterator(e.getValue())));
    }

    @Override
    public Status scan(String table, String startKey, int recordCount, Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
        var records = proxy.processScanRequest(table, startKey, recordCount, fields);
        records.ifPresent(r -> result.addAll(r.stream().map(DBClient::toByteIterator)
                .map(HashMap::new).collect(Collectors.toList())));
        return  records.isPresent() ? Status.OK : Status.NOT_FOUND;
    }

    @Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        var fields = values.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().toArray()));
        var updateReq = new PostRequest(table, key, fields);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoRequest(OP.UPDATE, updateReq));
        return getStatus(reply);
    }

    @Override
    public Status insert(String table, String key, Map<String, ByteIterator> values) {
        var fields = values.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().toArray()));
        var insertReq = new PostRequest(table, key, fields);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoRequest(OP.INSERT, insertReq));
        return getStatus(reply);
    }

    @Override
    public Status delete(String table, String key) {
        var deleteReq = new DeleteRequest(table, key);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoRequest(OP.DELETE, deleteReq));
        return getStatus(reply);
    }

    @NotNull
    private Status getStatus(byte[] reply) {
        if (reply.length == 0)
            return Status.ERROR;
        byte byteStatus = reply[0];
        if (byteStatus < 0 || byteStatus >= RETURN_CODES.values().length)
            return Status.ERROR;
        RETURN_CODES code = RETURN_CODES.values()[byteStatus];
        switch (code) {
            case OK:
                return Status.OK;
            case NOT_FOUND:
                return Status.NOT_FOUND;
            default:
                return Status.ERROR;
        }
    }

    public void reset() {
        proxy.reset();
    }

    public enum OP {
        UPDATE, INSERT, DELETE
    }

    public enum RETURN_CODES {
        OK, NOT_FOUND, ERROR
    }
}
