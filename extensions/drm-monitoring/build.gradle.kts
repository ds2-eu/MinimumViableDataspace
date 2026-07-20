/*
 *  Copyright (c) 2025 Contributor to Eclipse Foundation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

plugins {
    `java-library`
    `maven-publish`
}


group = "com.minsait.ds2.workshop.edc.extensions"
version = "0.0.1-0.14.1"


repositories {
    mavenCentral()
}

dependencies {
    // EDC SPI modules
    api(libs.edc.core.spi)
    api(libs.edc.controlplane.core)
    api(libs.edc.controlplane.services)
    api(libs.edc.transfer.spi)


    // Spring Boot Starter Web (RestTemplate, Jackson, etc.)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

publishing {
    publications {
        create<MavenPublication>("drm-monitoring") {
            artifactId = "drm-monitoring"
            from(components["java"])
        }
    }
}

tasks.test {
    useJUnitPlatform()
}