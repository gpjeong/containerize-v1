package com.containerize.service;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Jinja2-based template rendering for Dockerfiles using Jinjava (HubSpot)
 */
@Service
public class TemplateEngineService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateEngineService.class);

    private final Jinjava jinjava;

    public TemplateEngineService() {
        // Initialize Jinjava with configuration
        JinjavaConfig config = JinjavaConfig.newBuilder()
            .withTrimBlocks(true)
            .withLstripBlocks(true)
            .build();

        this.jinjava = new Jinjava(config);

        // Register custom filters
        registerCustomFilters();

        logger.info("TemplateEngineService initialized");
    }

    /**
     * Register custom Jinja2 filters
     */
    private void registerCustomFilters() {
        // Custom filter: tojson — wraps a value in JSON double quotes
        jinjava.getGlobalContext().registerFilter(new Filter() {
            @Override
            public String getName() {
                return "tojson";
            }

            @Override
            public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
                if (var == null) return "null";
                if (var instanceof String) {
                    return "\"" + ((String) var).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                }
                return var.toString();
            }
        });

        // Custom filter: split_jvm_options — splits a string by whitespace into a list
        jinjava.getGlobalContext().registerFilter(new Filter() {
            @Override
            public String getName() {
                return "split_jvm_options";
            }

            @Override
            public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
                if (var instanceof String) {
                    String options = (String) var;
                    return Arrays.stream(options.split("\\s+"))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            }
        });
    }

    /**
     * Render a template with the given context
     *
     * @param templateName Template file name (e.g., 'python/fastapi.dockerfile.j2')
     * @param context Template context variables
     * @return Rendered template content
     * @throws IOException if template cannot be loaded or rendered
     */
    public String render(String templateName, Map<String, Object> context) throws IOException {
        try {
            // Load template
            String templateContent = loadTemplate(templateName);

            if (templateContent == null) {
                throw new IOException("Template not found: " + templateName);
            }

            // Render template with context
            String rendered = jinjava.render(templateContent, context);

            logger.info("Rendered template: {}", templateName);
            return rendered;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to render template {}: {}", templateName, e.getMessage(), e);
            throw new IOException("Failed to render template: " + e.getMessage(), e);
        }
    }

    /**
     * Load template from classpath or filesystem
     *
     * @param templateName Template file name
     * @return Template content or null if not found
     */
    private String loadTemplate(String templateName) {
        // 1. Try classpath resource (works in JAR and IDE)
        String resourcePath = "/templates/" + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.debug("Could not load from classpath: {}", resourcePath);
        }

        // 2. Try Spring ClassPathResource
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + templateName);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not load via ClassPathResource: {}", templateName);
        }

        // 3. Fallback to filesystem (development mode)
        Path filePath = Paths.get("src/main/resources/templates").resolve(templateName);
        if (Files.exists(filePath)) {
            try {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("Error reading template file: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * List all available templates
     *
     * @return Templates organized by language
     */
    public Map<String, List<String>> listTemplates() {
        Map<String, List<String>> templates = new LinkedHashMap<>();
        templates.put("python", Arrays.asList("fastapi", "flask", "django"));
        templates.put("nodejs", Arrays.asList("express", "nestjs", "nextjs"));
        templates.put("java", Arrays.asList("spring-boot"));

        // Try to dynamically scan for additional templates
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String language : new String[]{"python", "nodejs", "java"}) {
                try {
                    Resource[] resources = resolver.getResources("classpath:templates/" + language + "/*.dockerfile.j2");
                    if (resources.length > 0) {
                        List<String> templateNames = new ArrayList<>();
                        for (Resource resource : resources) {
                            String filename = resource.getFilename();
                            if (filename != null && filename.endsWith(".dockerfile.j2")) {
                                templateNames.add(filename.replace(".dockerfile.j2", ""));
                            }
                        }
                        if (!templateNames.isEmpty()) {
                            templates.put(language, templateNames);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not scan templates for {}: {}", language, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Template scanning not available, using defaults");
        }

        return templates;
    }
}
