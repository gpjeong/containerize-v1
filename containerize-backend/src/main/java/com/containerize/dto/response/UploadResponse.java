package com.containerize.dto.response;

import com.containerize.dto.request.ProjectInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("size")
    private long size;

    @JsonProperty("project_info")
    private ProjectInfo projectInfo;
}
