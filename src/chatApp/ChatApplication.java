package chatApp;

import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.notifications.DeliverChatMessageNotification;
import valueDispatcher.requests.DisseminateChatMessageRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Scanner;

import static java.nio.file.StandardOpenOption.APPEND;

/**
 * Small app used to verify if the communication between nodes is working properly.
 */
public class ChatApplication extends GenericProtocol {

    private static final String CHAT_OUTPUT_LOG_FILE = "./chatLog.txt";

    private static final Logger logger = LogManager.getLogger(ChatApplication.class);

    private final String chatOutputLogFile;

    public ChatApplication(Properties props) throws HandlerRegistrationException, IOException {
        super(ChatApplication.class.getSimpleName(), IDGenerator.genId());
        subscribeNotification(DeliverChatMessageNotification.ID, (DeliverChatMessageNotification notif, short source) -> uponDeliverChatMessageNotification(notif));
        ProtoPojo.pojoSerializers.put(ChatMessage.ID, ChatMessage.serializer);

        this.chatOutputLogFile = props.getProperty("chatOutputLogFile", CHAT_OUTPUT_LOG_FILE);

        Path filePath = Path.of(chatOutputLogFile);
        Files.deleteIfExists(filePath);
        Files.createDirectories(filePath.getParent());
        Files.createFile(filePath);
    }

    @Override
    public void init(Properties properties) {
        readUserCommands();
    }

    public void readUserCommands() {
        new Thread(() -> {
            try(Scanner in = new Scanner(System.in)) {
                while(true) {
                    String message = in.nextLine();
                    if (message.equals("exit"))
                        break;
                    ChatMessage chatMessage = new ChatMessage(message);
                    sendRequest(new DisseminateChatMessageRequest(chatMessage), ValueDispatcher.ID);
                }
            }
        }).start();
    }

    private void uponDeliverChatMessageNotification(DeliverChatMessageNotification notif) {
        ChatMessage chatMessage = notif.getChatMessage();
        String messageContent = chatMessage.getMessage();
        logger.info("Received chat message with content: {}", messageContent);
        System.out.println(messageContent);
        try {
            Files.writeString(Path.of(chatOutputLogFile), messageContent + "\n", APPEND);
        } catch (IOException e) {
            logger.error("Unable to record chat message. Cause: {}", e.getMessage());
        }
    }
}
