package valueDispatcher.requests;

import chatApp.ChatMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateChatMessageRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final ChatMessage chatMessage;

    public DisseminateChatMessageRequest(ChatMessage chatMessage) {
        super(ID);
        this.chatMessage = chatMessage;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }
}
