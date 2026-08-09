package com.red.sovereign.dependencies;

/**
 * RED: Server Redirection Module
 * This file replaces RED's cloud endpoints with LOCAL server IPs.
 */
public class DevelopedServerConfig {

    // The Master Local IP of your RED Server
    public static final String LOCAL_IP = "http://192.168.1.50:8080";

    // Redirecting all RED Services to Local
    public static final String SIGNAL_URL = LOCAL_IP;
    public static final String SIGNAL_CDN_URL = LOCAL_IP + "/cdn";
    public static final String SIGNAL_CONTACT_DISCOVERY_URL = LOCAL_IP + "/directory";
    public static final String SIGNAL_KEY_BACKUP_URL = LOCAL_IP + "/backup";
    
    // PSTN / Dumin Gateway Endpoint
    public static final String DUMIN_GATEWAY_URL = "http://192.168.1.100:5060";
}
