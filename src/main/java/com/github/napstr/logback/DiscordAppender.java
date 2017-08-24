package com.github.napstr.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Post a Log Event to a Discord webhook
 * There are probably ratelimits for this, so don't overuse it. 95% of the time you want to log only fatal stuff.
 * <p>
 * Discord API docs:
 * https://discordapp.com/developers/docs/resources/webhook#execute-webhook
 */
public class DiscordAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private OkHttpClient client = new OkHttpClient();
    private ObjectMapper objectMapper = new ObjectMapper();

    private String webhookUri;
    private Layout<ILoggingEvent> layout;
    private String username;
    private String avatarUrl;


    @Override
    protected void append(final ILoggingEvent event) {

        if (webhookUri == null || webhookUri.isEmpty()) {
            String msg = "No webhookUri set, can't send logs to Discord.";
            addWarn(msg);
            System.err.println(msg);
            return;
        }

        String text = "";
        try {
            text = layout.doLayout(event);
            // omit empty code blocks
            text = text.replaceAll("```\\s```", "");

            boolean saveCodeBlock = text.endsWith("```"); //preserve end of a code block, a very likely use case
            if (text.length() > 2000) {
                if (saveCodeBlock) {
                    text = text.substring(0, 1997);
                    text += "```";
                } else
                    text = text.substring(0, 2000);
            }

            Map<String, String> message = new HashMap<>();
            message.put("content", text);
            message.put("username", username);
            message.put("avatar_url", avatarUrl);

            String json = objectMapper.writeValueAsString(message);

            post(webhookUri, json);
        } catch (Exception e) {
            String msg = "Error posting log to Discord: " + event + "\n" + text;
            addError(msg, e);
            System.err.println(msg);
            e.printStackTrace();
        }
    }

    private void post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() >= 300) {
                String msg = "Error posting log to Discord: Request returned " + response.code()
                        + "\nHeaders" + response.headers().toString()
                        + "\nBody:" + (response.body() != null ? response.body().string() : "Null body");
                addError(msg);
                System.err.println(msg);
            }
        }
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(final Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    public String getWebhookUri() {
        return webhookUri;
    }

    public void setWebhookUri(String webhookUri) {
        this.webhookUri = webhookUri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
