plugins {
    id("java")
    id("org.springframework.boot") version "2.7.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Include our sharding library
    implementation(project(":sharding-springboot-starter"))

    // Database drivers
    runtimeOnly("mysql:mysql-connector-java")
    runtimeOnly("org.postgresql:postgresql")

    // Optional Redis support for caching
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
}

tasks.test {
    useJUnitPlatform()
}