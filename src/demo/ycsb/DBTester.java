package demo.ycsb;

import org.junit.jupiter.api.Test;
import site.ycsb.ByteIterator;
import site.ycsb.Status;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DBTester {

    @Test
    void shouldNotFindRead() {
        var db = new DBClient();
        Set<String> fields = new HashSet<>();
        var status = db.read("users", "pablo", fields, new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindScan() {
        var db = new DBClient();
        var status = db.scan("users", "pablo", 10, new HashSet<>(), new Vector<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindUpdate() {
        var db = new DBClient();
        var status = db.update("users", "pablo", new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindDelete() {
        var db = new DBClient();
        var status = db.delete("users", "pablo");
        assertEquals(Status.NOT_FOUND, status);

    }

    @Test
    void shouldInsertNonExistentTable() {
        var db = new DBClient();
        var status = db.insert("users", "pablo", new HashMap<>());
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldReadExistentUser() {
        var db = new DBClient();
        db.insert("users", "pablo", new HashMap<>());
        var status = db.read("users", "pablo", new HashSet<>(), new HashMap<>());
        assertEquals(Status.OK, status);
    }


    @Test
    void shouldScanExistentUsers() {
        var db = new DBClient();
        db.insert("users", "a", new HashMap<>());
        db.insert("users", "c", new HashMap<>());
        Vector<HashMap<String, ByteIterator>> output = new Vector<>();
        var status = db.scan("users", "a", 2, new HashSet<>(), output);
        assertEquals(Status.OK, status);
        System.out.println(output);
    }

    @Test
    void shouldUpdateExistentUser() {
        var db = new DBClient();
        db.insert("users", "pablo", new HashMap<>());
        var status = db.update("users", "pablo", new HashMap<>());
        assertEquals(Status.OK, status);
    }

}
