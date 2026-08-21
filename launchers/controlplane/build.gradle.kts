/*
*  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
*
*  This program and the accompanying materials are made available under the
*  terms of the Apache License, Version 2.0 which is available at
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  SPDX-License-Identifier: Apache-2.0
*
*  Contributors:
*       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial API and Implementation
*
*/

plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    runtimeOnly(project(":extensions:did-example-resolver"))
    runtimeOnly(project(":extensions:dcp-impl")) // some patches/impls for DCP
    runtimeOnly(project(":extensions:catalog-node-resolver")) // to trigger the federated catalog
    runtimeOnly(project(":extensions:transfer-monitor")) // to test the new extention for control plane

    runtimeOnly(project(":extensions:drm-monitoring")) // to log events from the connector to the DRM

    runtimeOnly(libs.edc.bom.controlplane)
    runtimeOnly(libs.edc.api.secrets)

    implementation("com.minsait.onesait.platform.edc.extensions:ds2-policy-functions:0.0.40-0.14.1")
    implementation("com.minsait.onesait.platform.edc.extensions:metadata-service:0.0.40-0.14.1")
    implementation("com.minsait.onesait.platform.edc.extensions:ds2-policy-definition-storage:0.0.40-0.14.1")

    if (project.properties.getOrDefault("persistence", "false") == "true") {
        runtimeOnly(libs.edc.vault.hashicorp)
        runtimeOnly(libs.edc.bom.controlplane.sql)
        println("This runtime compiles with a remote STS client, Hashicorp Vault and PostgreSQL. You will need properly configured Postgres and HCV instances.")
    }
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
