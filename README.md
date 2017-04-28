A Logback appender [Logback](http://logback.qos.ch/) that can write logs to a [Discord](https://discordapp.com/) channel.

### Setup
[![Release](https://jitpack.io/v/napstr/logback-discord-appender.svg?style=flat-square)](https://jitpack.io/#napstr/logback-discord-appender)
Add through the [JitPack](https://jitpack.io/) repo to your project:
###### Maven pom.xml
```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>


    <dependency>
        <groupId>com.github.napstr</groupId>
        <artifactId>logback-discord-appender</artifactId>
        <version>0.0.1</version>
    </dependency>
```
###### Gradle build.gradle
```
    repositories {
        maven { url 'https://jitpack.io' }
    }


    dependencies {
        compile 'com.github.napstr:logback-discord-appender:0.0.1'
    }

```



Configure the appender in your logback.xml:

```xml
<configuration>
    <appender name="DISCORD" class="com.github.napstr.logback.DiscordAppender">
       <!-- do not set your webhook here if you want to commit this file to your VCS, instead look below for an example on how to set it at runtime -->
       <webhookUri>https://discordapp.com/api/webhooks/1234567890/abcdefghijklmnopqrstuvwxyz</webhookUri>
        <layout class="ch.qos.logback.classic.PatternLayout">
            <pattern>%d{HH:mm:ss} [%thread] [%-5level] %logger{36} - %msg%n```%ex{full}```</pattern>
        </layout>
        <username></username>
        <avatarUrl></avatarUrl>
    </appender>

    <appender name="ASYNC_DISCORD" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="DISCORD" />
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
    </appender>

    <root>
        <level value="DEBUG" />
        <appender-ref ref="ASYNC_DISCORD" />
    </root>

</configuration>
```

Treat your webhookUri the same way you treat your bot token, keep it secret and do not commit it to your VCS.
You can set the webhookUri at runtime with this code:

```java
    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    AsyncAppender discordAsync = (AsyncAppender) lc.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_DISCORD");
    DiscordAppender discordAppender = (DiscordAppender) discordAsync.getAppender("DISCORD");
    discordAppender.setWebhookUri(yourSecretWebhookUri);
```


### Todos:
- Enforce Discord ratelimits
- Support for tts, embeds and/or files