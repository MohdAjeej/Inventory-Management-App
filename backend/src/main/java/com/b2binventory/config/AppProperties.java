package com.b2binventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Uploads uploads, Invitations invitations) {

    public record Uploads(String dir) {
    }

    public record Invitations(int validDays) {
    }
}
