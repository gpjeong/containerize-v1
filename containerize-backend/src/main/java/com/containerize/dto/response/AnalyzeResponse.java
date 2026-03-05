package com.containerize.dto.response;

import com.containerize.dto.request.ProjectInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResponse {

    @JsonProperty("project_info")
    private ProjectInfo projectInfo;

    @JsonProperty("suggestions")
    private Map<String, String> suggestions = new HashMap<>();
}
