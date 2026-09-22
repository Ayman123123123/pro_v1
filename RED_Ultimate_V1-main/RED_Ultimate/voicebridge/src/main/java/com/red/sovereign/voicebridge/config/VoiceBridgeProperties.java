package com.red.sovereign.voicebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "voicebridge")
@Validated
public class VoiceBridgeProperties {

    @NotBlank
    private String stasisAppName = "voicebridge";

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer maxConcurrentCalls = 100;

    @NotNull
    @Min(10)
    @Max(300)
    private Integer callTimeoutSeconds = 180;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer rtpPortPoolSize = 50;

    private Boolean bargeInEnabled = true;

    private Boolean recordingEnabled = false;

    private String recordingPath = "/var/spool/asterisk/recordings";

    // Getters and Setters
    public String getStasisAppName() { return stasisAppName; }
    public void setStasisAppName(String stasisAppName) { this.stasisAppName = stasisAppName; }

    public Integer getMaxConcurrentCalls() { return maxConcurrentCalls; }
    public void setMaxConcurrentCalls(Integer maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }

    public Integer getCallTimeoutSeconds() { return callTimeoutSeconds; }
    public void setCallTimeoutSeconds(Integer callTimeoutSeconds) { this.callTimeoutSeconds = callTimeoutSeconds; }

    public Integer getRtpPortPoolSize() { return rtpPortPoolSize; }
    public void setRtpPortPoolSize(Integer rtpPortPoolSize) { this.rtpPortPoolSize = rtpPortPoolSize; }

    public Boolean getBargeInEnabled() { return bargeInEnabled; }
    public void setBargeInEnabled(Boolean bargeInEnabled) { this.bargeInEnabled = bargeInEnabled; }

    public Boolean getRecordingEnabled() { return recordingEnabled; }
    public void setRecordingEnabled(Boolean recordingEnabled) { this.recordingEnabled = recordingEnabled; }

    public String getRecordingPath() { return recordingPath; }
    public void setRecordingPath(String recordingPath) { this.recordingPath = recordingPath; }
}