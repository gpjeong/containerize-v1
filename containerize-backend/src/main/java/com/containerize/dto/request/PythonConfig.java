package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PythonConfig extends BaseDockerConfig {

    @JsonProperty("package_manager")
    private String packageManager;

    @JsonProperty("server")
    private String server;

    @JsonProperty("requirements_content")
    private String requirementsContent;

    @JsonProperty("entrypoint_file")
    private String entrypointFile = "main.py";
}
