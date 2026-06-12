/*
 *  Copyright (c) 2024 Contributor to Eclipse Foundation
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

dependencies {
    api(libs.edc.core.spi)
    api(libs.edc.dataplane.spi)
    api(libs.edc.transfer.spi)  
    api(libs.edc.dataplane.core)
    api(libs.edc.lib.token)
    api(libs.edc.core.api)
    
    api(libs.micrometer)
    
    implementation(libs.jakarta.rsApi)
    implementation(libs.micrometer)
    implementation(libs.edc.sql.core)
    implementation(libs.edc.sql.pool)
    implementation(libs.postgres)  // PostgreSQL driver
    implementation(libs.jakarta.websocket.api)
    implementation(libs.jetty.websocket.server)
    
}

publishing {
    publications {
        create<MavenPublication>("data-plane-metrics") {
            artifactId = "data-plane-metrics"
            from(components["java"])
        }
    }
} 