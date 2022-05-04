package valueDispatcher.notifications;

import chatApp.ChatMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverChatMessageNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final ChatMessage chatMessage;

    public DeliverChatMessageNotification(ChatMessage chatMessage) {
        super(ID);
        this.chatMessage = chatMessage;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }
}
