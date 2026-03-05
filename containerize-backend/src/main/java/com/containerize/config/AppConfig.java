package com.containerize.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {

    private Upload upload = new Upload();
    private Template template = new Template();
    private Cors cors = new Cors();
    private Ssl ssl = new Ssl();
    private RateLimit rateLimit = new RateLimit();

    @PostConstruct
    public void init() throws IOException {
        Path uploadPath = Paths.get(upload.getDirectory());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    public Path getUploadPath() {
        return Paths.get(upload.getDirectory());
    }

    public String getUploadDir() {
        return upload.getDirectory();
    }

    public String getTemplateDir() {
        return template.getDirectory();
    }

    public Set<String> getAllowedExtensionSet() {
        return Arrays.stream(upload.getAllowedExtensions().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    public List<String> getAllowedContentTypeList() {
        return Arrays.stream(upload.getAllowedContentTypes().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    public List<String> getCorsOriginList() {
        return Arrays.stream(cors.getAllowedOrigins().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @Getter
    @Setter
    public static class Upload {
        private String directory = "./uploads";
        private long maxSize = 536870912L; // 500MB
        private String allowedExtensions = ".jar,.war";
        private String allowedContentTypes = "application/java-archive,application/x-java-archive";
        private int cleanupDelaySeconds = 3600;
    }

    @Getter
    @Setter
    public static class Template {
        private String directory = "classpath:templates";
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:8000,http://localhost:4000,http://localhost:5173";
    }

    @Getter
    @Setter
    public static class Ssl {
        private boolean trustAll = false;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int generalRequestsPerMinute = 30;
        private int heavyRequestsPerMinute = 10;
    }
}
