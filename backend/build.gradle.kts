// Spring Boot 4 is GA since late 2025; we pin the latest 4.0.x patch release
// available at the time of writing (April 2026).
plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

extra["springdocVersion"] = "3.0.3"
extra["jjwtVersion"] = "0.12.6"
extra["zxingVersion"] = "3.5.3"
extra["testcontainersVersion"] = "1.21.4"
extra["telegramBotsVersion"] = "9.0.0"
extra["mockwebserverVersion"] = "4.12.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-context-support")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Liquibase + Postgres.
    // In Spring Boot 4 the auto-configuration for Liquibase moved out of
    // spring-boot-autoconfigure into its own module; pull it in via the
    // dedicated starter so that `spring.liquibase.*` properties bind.
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    runtimeOnly("org.postgresql:postgresql")

    // JWT (jjwt 0.12.x)
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // OpenAPI / Swagger UI (springdoc-openapi).
    // springdoc 3.0.x is the line that targets Spring Boot 4 / Spring Framework 7.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // ZXing — pre-added for Task 08 (QR codes for public book lists).
    implementation("com.google.zxing:core:${property("zxingVersion")}")
    implementation("com.google.zxing:javase:${property("zxingVersion")}")

    // Telegram Bot API client + long polling (rubenlagus/TelegramBots).
    // Deliberately NOT the Spring Boot starters: the library's API objects are
    // annotated with Jackson 2, while Spring Boot 4 serializes MVC payloads with
    // Jackson 3 (tools.jackson) — routing library types through Spring's message
    // converters fails to deserialize them. Long polling keeps the library's own
    // Jackson 2 mapper internal, so the two never meet.
    implementation("org.telegram:telegrambots-client:${property("telegramBotsVersion")}")
    implementation("org.telegram:telegrambots-longpolling:${property("telegramBotsVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 split out the MockMvc test slice (AutoConfigureMockMvc etc.)
    // into a dedicated starter; needed for @AutoConfigureMockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Stands in for api.telegram.org when testing the bot.
    testImplementation("com.squareup.okhttp3:mockwebserver:${property("mockwebserverVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
