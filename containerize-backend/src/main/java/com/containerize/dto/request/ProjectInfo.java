package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInfo {

    @JsonProperty("language")
    private String language;

    @JsonProperty("framework")
    private String framework;

    @JsonProperty("detected_version")
    private String detectedVersion;

    @JsonProperty("build_tool")
    private String buildTool;

    @JsonProperty("main_class")
    private String mainClass;

    @JsonProperty("dependencies")
    private List<String> dependencies = new ArrayList<>();

    @JsonProperty("metadata")
    private Map<String, String> metadata = new HashMap<>();
}
