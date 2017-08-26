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
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

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

    private final BlockingDeque<String> queue = new LinkedBlockingDeque<>();

    public DiscordAppender() {
        new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    post(queue.take());
                } catch (InterruptedException | IOException e) {
                    System.err.println("Error posting to Discord");
                    e.printStackTrace();
                }
            }
        }, "discord-appender").start();
    }

    @SuppressWarnings("ConstantConditions")
    private void post(String text) throws IOException {
        try {
            if (webhookUri == null || webhookUri.isEmpty()) {
                queue.addFirst(text);
                Thread.sleep(1000);
                return;
            }

            Map<String, String> message = new HashMap<>();
            message.put("content", text);
            message.put("username", username);
            message.put("avatar_url", avatarUrl);

            String json = objectMapper.writeValueAsString(message);

            RequestBody body = RequestBody.create(JSON, json);
            Request request = new Request.Builder()
                    .url(webhookUri)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.code() < 300) {
                    //things are fine
                } else if (response.code() == 429) {
                    //ratelimited. oh no
                    queue.addFirst(text);
                } else {
                    String msg = "Error posting log to Discord: Request returned " + response.code()
                            + "\nHeaders" + response.headers().toString()
                            + "\nBody:" + (response.body() != null ? response.body().string() : "Null body");
                    addError(msg);
                    System.err.println(msg);
                    queue.addFirst("⚠ Unexpected response while posting to discord ⚠\n" + msg);
                }

                //NOTE: webhooks have a 30/60s ratelimit, but if we are posting to the same channel, the channel
                // ratelimit of 5/5s appears to also apply. However the responses discord sends never show the ratelimit
                // for that until it gets hit, so we can't really anticipate it.
                int rateLimitRemaining = Integer.valueOf(response.header("X-RateLimit-Remaining", "0"));
                if (rateLimitRemaining == 0) {
                    String ratelimitReset = response.header("X-RateLimit-Reset");
                    long sleep;
                    if (ratelimitReset != null) {
                        sleep = Long.valueOf(ratelimitReset) * 1000 - System.currentTimeMillis();
                    } else {
                        sleep = 5000;
                    }
                    Thread.sleep(Math.max(0, sleep));
                }

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void append(final ILoggingEvent event) {
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
            queue.addLast(text);
        } catch (Exception e) {
            String msg = "Error posting log to Discord: " + event + "\n" + text;
            addError(msg, e);
            System.err.println(msg);
            e.printStackTrace();
        }
    }

    //the setters below which look like boilerplate code  are actually important so that the config from the logback.xml
    //can be set by logback
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
