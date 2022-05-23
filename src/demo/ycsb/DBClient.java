package demo.ycsb;

import demo.ycsb.pojos.DeleteRequest;
import demo.ycsb.pojos.PostRequest;
import demo.ycsb.serializers.GenericPojoSerializer;
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

    private final OperationProcessor proxy = new OperationProcessor(new String[0]);

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
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.UPDATE, updateReq));
        return reply.length == 0 ? Status.NOT_FOUND : Status.OK;
    }

    @Override
    public Status insert(String table, String key, Map<String, ByteIterator> values) {
        var fields = values.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().toArray()));
        var insertReq = new PostRequest(table, key, fields);
        proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.INSERT, insertReq));
        return Status.OK;
    }

    @Override
    public Status delete(String table, String key) {
        var deleteReq = new DeleteRequest(table, key);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.DELETE, deleteReq));
        return reply.length == 0 ? Status.NOT_FOUND : Status.OK;
    }

    public enum OP {
        UPDATE, INSERT, DELETE
    }
}
