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
public class GenerateResponse {

    @JsonProperty("dockerfile")
    private String dockerfile;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("metadata")
    private Map<String, String> metadata = new HashMap<>();
}
