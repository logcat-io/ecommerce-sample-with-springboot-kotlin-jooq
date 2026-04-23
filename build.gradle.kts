plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"

    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"

    id("nu.studer.jooq") version "9.0"
}

group = "org.ecommerce"
version = "0.0.1-SNAPSHOT"
description = "ecommerce-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["jooqVersion"] = "3.19.29"
val jooqVersion: String by extra

fun env(key: String, default: String): String =
    System.getenv(key)
        ?: project.findProperty(key)?.toString()
        ?: default

val dbUrl = env("DB_URL", "jdbc:postgresql://localhost:5437/ecommerce")
val dbUsername = env("DB_USERNAME", "ecommerce")
val dbPassword = env("DB_PASSWORD", "ecommerce")
val dbDriver = "org.postgresql.Driver"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // jOOQ codegen
    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")
    jooqGenerator(project(":custom-strategy"))
    jooqGenerator("org.postgresql:postgresql")

    // Flyway
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // UUID v7
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")

    testImplementation("com.redis:testcontainers-redis")
}


jooq {
    version.set(jooqVersion)

    configurations {
        create("main").apply {
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration.apply {
                jdbc.apply {
                    driver = dbDriver
                    url = dbUrl
                    user = dbUsername
                    password = dbPassword
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    strategy.apply {
                        name = "org.dispatch.kotlinjooq.PrefixGeneratorStrategy"
                    }

                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = ".*"
                        excludes = "flyway_schema_history"
                    }

                    generate.apply {
                        isRecords = true
                        isDaos = true
                        isFluentSetters = true
                        isJavaTimeTypes = true
                        isDeprecated = false
                    }

                    target.apply {
                        packageName = "org.ecommerce.jooq.generated"
                        directory = "src/main/generated"
                    }
                }
            }
        }
    }
}

// jOOQ 생성 코드를 소스셋에 추가
sourceSets {
    main {
        kotlin {
            srcDir("src/main/generated")
        }
    }
}

// Kotlin Compiler Config
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
