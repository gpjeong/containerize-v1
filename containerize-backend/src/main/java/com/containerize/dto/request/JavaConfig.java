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
public class JavaConfig extends BaseDockerConfig {

    @JsonProperty("build_tool")
    private String buildTool;

    @JsonProperty("jar_file_name")
    private String jarFileName;

    @JsonProperty("main_class")
    private String mainClass;

    @JsonProperty("jvm_options")
    private String jvmOptions = "-Xmx512m";

    @JsonProperty("build_file_content")
    private String buildFileContent;
}
