package demo.ycsb.pojos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest implements Serializable {
    private String table, startKey;
    private int recordCount;
    private Set<String> fields;
}
