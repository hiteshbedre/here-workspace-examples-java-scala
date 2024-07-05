import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

import java.io.IOException;

public class SlackMessageSender {

    private static final String BOT_TOKEN = "xoxb-your-bot-token"; // Replace with your Bot User OAuth Access Token
    private static final String CHANNEL_ID = "C0123456789"; // Replace with your channel ID

    public static void main(String[] args) {
        String username = "john_doe";
        String email = "john_doe@example.com";
        sendSlackMessage(username, email);
    }

    public static void sendSlackMessage(String username, String email) {
        Slack slack = Slack.getInstance();
        String message = String.format("New user registered:\nUsername: %s\nEmail: %s", username, email);

        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(CHANNEL_ID)
                .text(message)
                .build();

        try {
            ChatPostMessageResponse response = slack.methods(BOT_TOKEN).chatPostMessage(request);
            if (response.isOk()) {
                System.out.println("Message sent successfully.");
            } else {
                System.out.println("Error sending message: " + response.getError());
            }
        } catch (IOException | SlackApiException e) {
            e.printStackTrace();
        }
    }
}
