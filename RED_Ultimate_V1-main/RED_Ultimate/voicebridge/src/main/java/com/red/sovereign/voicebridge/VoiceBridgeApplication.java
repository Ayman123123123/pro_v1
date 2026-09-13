package com.red.sovereign.voicebridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({VoiceBridgeProperties.class, AriProperties.class, RtpProperties.class, BotProperties.class})
public class VoiceBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceBridgeApplication.class, args);
    }
}