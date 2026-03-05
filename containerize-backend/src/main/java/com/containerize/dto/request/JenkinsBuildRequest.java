package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JenkinsBuildRequest {

    @JsonProperty("config")
    private Map<String, Object> config;

    @JsonProperty("jenkins_url")
    private String jenkinsUrl;

    @JsonProperty("jenkins_job")
    private String jenkinsJob;

    @JsonProperty("jenkins_token")
    private String jenkinsToken;

    @JsonProperty("jenkins_username")
    private String jenkinsUsername;

    @JsonProperty("git_url")
    private String gitUrl;

    @JsonProperty("git_branch")
    private String gitBranch;

    @JsonProperty("git_credential_id")
    private String gitCredentialId;

    @JsonProperty("image_name")
    private String imageName;

    @JsonProperty("image_tag")
    private String imageTag;

    @JsonProperty("use_kubernetes")
    private boolean useKubernetes;

    @JsonProperty("use_kaniko")
    private boolean useKaniko;

    @JsonProperty("harbor_url")
    private String harborUrl;

    @JsonProperty("harbor_credential_id")
    private String harborCredentialId;
}
