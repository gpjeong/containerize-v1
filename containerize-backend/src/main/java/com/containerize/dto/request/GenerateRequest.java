package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRequest {

    @JsonProperty("project_info")
    private ProjectInfo projectInfo;

    @JsonProperty("config")
    private Map<String, Object> config;
}
