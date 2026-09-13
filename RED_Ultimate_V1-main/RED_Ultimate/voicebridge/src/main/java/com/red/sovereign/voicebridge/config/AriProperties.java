package com.red.sovereign.voicebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@ConfigurationProperties(prefix = "voicebridge.ari")
@Validated
public class AriProperties {

    @NotBlank
    private String host = "localhost";

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port = 8088;

    @NotBlank
    private String username = "voicebridge";

    @NotBlank
    private String password = "changeme";

    @NotBlank
    @Pattern(regexp = "^(http|ws)s?://.+")
    private String baseUrl;

    @NotBlank
    private String webSocketPath = "/ari/events";

    private Integer connectTimeoutMs = 5000;

    private Integer readTimeoutMs = 30000;

    private Boolean autoReconnect = true;

    private Integer reconnectIntervalMs = 5000;

    // Getters and Setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getWebSocketPath() { return webSocketPath; }
    public void setWebSocketPath(String webSocketPath) { this.webSocketPath = webSocketPath; }

    public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public Integer getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public Boolean getAutoReconnect() { return autoReconnect; }
    public void setAutoReconnect(Boolean autoReconnect) { this.autoReconnect = autoReconnect; }

    public Integer getReconnectIntervalMs() { return reconnectIntervalMs; }
    public void setReconnectIntervalMs(Integer reconnectIntervalMs) { this.reconnectIntervalMs = reconnectIntervalMs; }
}