plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ar.edu.odontogestion"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Frontend empaquetado en el mismo origen (opt-in): ./gradlew bootJar -PwithFrontend
// Sin la propiedad el build no requiere Node. El build de Vite se copia a la salida de recursos
// (build/resources/main/static), nunca a src/, para no dejar archivos generados en el repositorio.
if (providers.gradleProperty("withFrontend").isPresent) {
    val frontendDir = layout.projectDirectory.dir("../frontend")
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

    val npmCi = tasks.register<Exec>("npmCi") {
        workingDir = frontendDir.asFile
        commandLine(npm, "ci")
        inputs.files(frontendDir.file("package.json"), frontendDir.file("package-lock.json"))
        outputs.dir(frontendDir.dir("node_modules"))
    }

    val npmBuild = tasks.register<Exec>("npmBuild") {
        dependsOn(npmCi)
        workingDir = frontendDir.asFile
        commandLine(npm, "run", "build")
        inputs.dir(frontendDir.dir("src"))
        inputs.files(
            frontendDir.file("index.html"),
            frontendDir.file("package.json"),
            frontendDir.file("tsconfig.json"),
            frontendDir.file("vite.config.ts"),
        )
        outputs.dir(frontendDir.dir("dist"))
    }

    tasks.processResources {
        dependsOn(npmBuild)
        from(frontendDir.dir("dist")) {
            into("static")
        }
    }
}
