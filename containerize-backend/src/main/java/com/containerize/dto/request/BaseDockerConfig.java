package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseDockerConfig {

    @JsonProperty("language")
    private String language;

    @JsonProperty("framework")
    private String framework;

    @JsonProperty("runtime_version")
    private String runtimeVersion;

    @JsonProperty("port")
    @Min(1)
    @Max(65535)
    private int port = 8000;

    @JsonProperty("environment_vars")
    private Map<String, String> environmentVars = new HashMap<>();

    @JsonProperty("health_check_path")
    private String healthCheckPath = "/health";

    @JsonProperty("base_image")
    private String baseImage;

    @JsonProperty("user")
    private String user = "appuser";

    @JsonProperty("system_dependencies")
    private List<String> systemDependencies = new ArrayList<>();

    @JsonProperty("service_url")
    private String serviceUrl;

    @JsonProperty("custom_start_command")
    private String customStartCommand;
}
