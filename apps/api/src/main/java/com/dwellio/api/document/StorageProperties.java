package com.dwellio.api.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dwellio.storage")
public class StorageProperties {

    private String type = "s3";
    private String bucket = "dwellio-documents";
    private String region = "us-east-1";
    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private boolean pathStyle = true;
    private long presignTtlSeconds = 900;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public void setPathStyle(boolean pathStyle) {
        this.pathStyle = pathStyle;
    }

    public long getPresignTtlSeconds() {
        return presignTtlSeconds;
    }

    public void setPresignTtlSeconds(long presignTtlSeconds) {
        this.presignTtlSeconds = presignTtlSeconds;
    }
}
