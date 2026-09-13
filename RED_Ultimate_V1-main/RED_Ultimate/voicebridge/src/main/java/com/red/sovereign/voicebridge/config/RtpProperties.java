package com.red.sovereign.voicebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "voicebridge.rtp")
@Validated
public class RtpProperties {

    @NotNull
    @Min(1024)
    @Max(65535)
    private Integer bindPort = 12100;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer portRangeStart = 12100;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer portRangeEnd = 13100;

    @NotNull
    @Min(10)
    @Max(100)
    private Integer frameSizeMs = 20;

    @NotNull
    @Min(8000)
    @Max(48000)
    private Integer sampleRate = 8000;

    @NotBlank
    private String defaultCodec = "PCMU";

    private Boolean symmetricRtp = true;

    private Boolean learnPeer = true;

    private Integer jitterBufferSize = 100;

    // Getters and Setters
    public Integer getBindPort() { return bindPort; }
    public void setBindPort(Integer bindPort) { this.bindPort = bindPort; }

    public Integer getPortRangeStart() { return portRangeStart; }
    public void setPortRangeStart(Integer portRangeStart) { this.portRangeStart = portRangeStart; }

    public Integer getPortRangeEnd() { return portRangeEnd; }
    public void setPortRangeEnd(Integer portRangeEnd) { this.portRangeEnd = portRangeEnd; }

    public Integer getFrameSizeMs() { return frameSizeMs; }
    public void setFrameSizeMs(Integer frameSizeMs) { this.frameSizeMs = frameSizeMs; }

    public Integer getSampleRate() { return sampleRate; }
    public void setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; }

    public String getDefaultCodec() { return defaultCodec; }
    public void setDefaultCodec(String defaultCodec) { this.defaultCodec = defaultCodec; }

    public Boolean getSymmetricRtp() { return symmetricRtp; }
    public void setSymmetricRtp(Boolean symmetricRtp) { this.symmetricRtp = symmetricRtp; }

    public Boolean getLearnPeer() { return learnPeer; }
    public void setLearnPeer(Boolean learnPeer) { this.learnPeer = learnPeer; }

    public Integer getJitterBufferSize() { return jitterBufferSize; }
    public void setJitterBufferSize(Integer jitterBufferSize) { this.jitterBufferSize = jitterBufferSize; }
}