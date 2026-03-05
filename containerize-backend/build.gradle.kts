plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.containerize"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Template Engine - Jinjava (Jinja2 compatible for Java)
    implementation("com.hubspot.jinjava:jinjava:2.7.3")

    // HTTP Client (for SSL configuration)
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // File Processing
    implementation("commons-io:commons-io:2.15.1")

    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Configure Spring Boot JAR
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("containerize-backend.jar")
}
