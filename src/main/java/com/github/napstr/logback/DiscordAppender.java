package com.github.napstr.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

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
    private static final MediaType FORM = MediaType.parse("multipart/form-data; charset=utf-8");

    private OkHttpClient client = new OkHttpClient();
    private ObjectMapper objectMapper = new ObjectMapper();

    private String webhookUri;
    private Layout<ILoggingEvent> layout;
    private String username;
    private String avatarUrl;


    @Override
    protected void append(final ILoggingEvent event) {

        if (webhookUri == null || "".equals(webhookUri)) {
            addWarn("No webhookUri set, can't send logs to Discord.");
            return;
        }

        try {
            String text = layout.doLayout(event);
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
            e.printStackTrace();
            addError("Error posting log to Discord: " + event, e);
        }
    }

    private void post(String uri, String json) throws IOException {
        String url = uri + "?wait=true";
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        if (response.code() != 200) {
            addError("Error posting log to Discord: Request returned " + response.code() + " " + response.body().string());
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
