plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.freefair.lombok") version "8.4"
}

group = "com.valarpirai"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework:spring-jdbc")
    implementation("com.zaxxer:HikariCP")
    implementation("org.slf4j:slf4j-api")

    // Caching support
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Optional Redis support
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.springframework.data:spring-data-redis")

    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Optional dependencies for database drivers
    compileOnly("mysql:mysql-connector-java")
    compileOnly("org.postgresql:postgresql")
    compileOnly("com.microsoft.sqlserver:mssql-jdbc")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 database for testing
    testRuntimeOnly("com.h2database:h2")

    // Test Redis (embedded)
    testImplementation("it.ozimov:embedded-redis:0.7.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = true
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}