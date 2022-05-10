package applicationInterface;

import org.apache.commons.lang3.tuple.Pair;

@FunctionalInterface
public interface ReplyListener {

    void processReply(Pair<byte[], Long> operationResult);

}
