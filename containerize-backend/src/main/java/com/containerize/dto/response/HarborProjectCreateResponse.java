package com.containerize.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarborProjectCreateResponse {

    @JsonProperty("project_name")
    private String projectName;

    @JsonProperty("project_url")
    private String projectUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("settings")
    private Map<String, Object> settings = new HashMap<>();
}
