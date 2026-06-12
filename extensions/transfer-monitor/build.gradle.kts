/*
 *  Copyright (c) 2025 Your Organization
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
    api(libs.edc.transfer.spi)    
    api(libs.edc.core.api)
    
    // Add these required dependencies 
    implementation(libs.jakarta.rsApi)   
    implementation(libs.edc.sql.core)
    implementation(libs.edc.sql.lease)
    implementation(libs.edc.sql.pool)
    implementation(libs.edc.sql.transactionlocal)
    implementation(libs.edc.controlplane.core)
    implementation(libs.edc.sql.transferprocess) // This is the critical one you're missing
    implementation(libs.jakarta.websocket.api)
    implementation(libs.jetty.websocket.server)

    
    // For JSON serialization
    implementation(libs.jackson.datatype.jakarta.jsonp)
}

publishing {
    publications {
        create<MavenPublication>("transfer-monitor") {
            artifactId = "transfer-monitor"
            from(components["java"])
        }
    }
}
