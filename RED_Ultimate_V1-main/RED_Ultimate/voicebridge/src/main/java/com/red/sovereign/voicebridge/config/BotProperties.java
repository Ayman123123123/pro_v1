package com.red.sovereign.voicebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "voicebridge.bot")
@Validated
public class BotProperties {

    @NotBlank
    private String provider = "openai-realtime";

    @NotBlank
    private String openaiApiKey;

    @NotBlank
    private String openaiRealtimeUrl = "wss://api.openai.com/v1/realtime";

    @NotBlank
    private String openaiModel = "gpt-4o-realtime-preview-2024-12-17";

    @NotBlank
    private String systemPrompt = "You are a helpful AI assistant for RED Sovereign communications platform.";

    private Double temperature = 0.8;

    private Integer maxTokens = 4096;

    private Boolean vadEnabled = true;

    private Double vadThreshold = 0.5;

    private Integer vadPrefixPaddingMs = 300;

    private Integer vadSilenceDurationMs = 500;

    private Boolean truncationEnabled = true;

    private String externalBotWsUrl;

    // Getters and Setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getOpenaiApiKey() { return openaiApiKey; }
    public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }

    public String getOpenaiRealtimeUrl() { return openaiRealtimeUrl; }
    public void setOpenaiRealtimeUrl(String openaiRealtimeUrl) { this.openaiRealtimeUrl = openaiRealtimeUrl; }

    public String getOpenaiModel() { return openaiModel; }
    public void setOpenaiModel(String openaiModel) { this.openaiModel = openaiModel; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Boolean getVadEnabled() { return vadEnabled; }
    public void setVadEnabled(Boolean vadEnabled) { this.vadEnabled = vadEnabled; }

    public Double getVadThreshold() { return vadThreshold; }
    public void setVadThreshold(Double vadThreshold) { this.vadThreshold = vadThreshold; }

    public Integer getVadPrefixPaddingMs() { return vadPrefixPaddingMs; }
    public void setVadPrefixPaddingMs(Integer vadPrefixPaddingMs) { this.vadPrefixPaddingMs = vadPrefixPaddingMs; }

    public Integer getVadSilenceDurationMs() { return vadSilenceDurationMs; }
    public void setVadSilenceDurationMs(Integer vadSilenceDurationMs) { this.vadSilenceDurationMs = vadSilenceDurationMs; }

    public Boolean getTruncationEnabled() { return truncationEnabled; }
    public void setTruncationEnabled(Boolean truncationEnabled) { this.truncationEnabled = truncationEnabled; }

    public String getExternalBotWsUrl() { return externalBotWsUrl; }
    public void setExternalBotWsUrl(String externalBotWsUrl) { this.externalBotWsUrl = externalBotWsUrl; }
}