package demo.ycsb;

import demo.ycsb.pojos.DeleteRequest;
import demo.ycsb.pojos.PostRequest;
import demo.ycsb.pojos.ReadRequest;
import demo.ycsb.pojos.ScanRequest;
import demo.ycsb.serializers.GenericPojoSerializer;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

public class DBClient extends DB {

    private final OperationProcessor proxy = new OperationProcessor(new String[0]);

    //@Override
    public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
        var readReq = new ReadRequest(table, key, fields);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.READ, readReq));
        if (reply.length == 0)
            return Status.NOT_FOUND;
        result.putAll(GenericPojoSerializer.deserialize(reply));
        return Status.OK;
    }

    //@Override
    public Status scan(String table, String startKey, int recordCount, Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
        var scanReq = new ScanRequest(table, startKey, recordCount, fields);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.SCAN, scanReq));
        if (reply.length == 0)
            return Status.NOT_FOUND;
        result.addAll(GenericPojoSerializer.deserialize(reply));
        return Status.OK;
    }

    //@Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        var fields = values.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray()));
        var updateReq = new PostRequest(table, key, fields);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.UPDATE, updateReq));
        return reply.length == 0 ? Status.NOT_FOUND : Status.OK;
    }

    //@Override
    public Status insert(String table, String key, Map<String, ByteIterator> values) {
        var fields = values.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray()));
        var insertReq = new PostRequest(table, key, fields);
        proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.INSERT, insertReq));
        return Status.OK;
    }

    //@Override
    public Status delete(String table, String key) {
        var deleteReq = new DeleteRequest(table, key);
        var reply = proxy.invokeOp(GenericPojoSerializer.serializePojoCode(OP.DELETE, deleteReq));
        return reply.length == 0 ? Status.NOT_FOUND : Status.OK;
    }

    public enum OP {
        READ, SCAN, UPDATE, INSERT, DELETE
    }
}
