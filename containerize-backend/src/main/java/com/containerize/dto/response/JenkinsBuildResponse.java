package com.containerize.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JenkinsBuildResponse {

    @JsonProperty("job_name")
    private String jobName;

    @JsonProperty("queue_id")
    private String queueId;

    @JsonProperty("queue_url")
    private String queueUrl;

    @JsonProperty("job_url")
    private String jobUrl;

    @JsonProperty("build_number")
    private Integer buildNumber;

    @JsonProperty("build_url")
    private String buildUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;
}
