package com.containerize.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NodeJSConfig extends BaseDockerConfig {

    @JsonProperty("package_manager")
    private String packageManager;

    @JsonProperty("package_json")
    private Map<String, Object> packageJson;

    @JsonProperty("build_command")
    private String buildCommand;

    @JsonProperty("start_command")
    private String startCommand;
}
