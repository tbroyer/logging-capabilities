/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.util.GradleVersion

plugins {
    `java-gradle-plugin`
    groovy
    id("com.gradle.plugin-publish") version "0.21.0"
    id("com.github.hierynomus.license") version "0.16.1"
}

repositories {
    mavenCentral()
}

group = "dev.jacomet.gradle.plugins"
version = "0.10.0"

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(gradleApi())

    testImplementation("org.spockframework:spock-core:2.0-groovy-3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

gradlePlugin {
    plugins {
        create("logging-capabilities") {
            id = "dev.jacomet.logging-capabilities"
            implementationClass = "dev.jacomet.gradle.plugins.logging.LoggingCapabilitiesPlugin"
            displayName = "Logging libraries capabilities"
            description = """Release notes:
                |* Adds a capability for the Log4j implementation named `log4j-impl`
                |* Leverages the Log4j BOM for Log4j alignment
            """.trimMargin()
        }
    }
}

pluginBundle {
    website = "https://github.com/ljacomet/logging-capabilities"
    vcsUrl = "https://github.com/ljacomet/logging-capabilities.git"
    tags = listOf("dependency", "dependencies", "dependency-management", "logging", "slf4j", "log4j2")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()
    }
}

license {
    header = rootProject.file("config/HEADER.txt")
    strictCheck = true
    ignoreFailures = true
    mapping(mapOf(
        "java"   to "SLASHSTAR_STYLE",
        "kt"     to "SLASHSTAR_STYLE",
        "groovy" to "SLASHSTAR_STYLE",
        "kts"    to "SLASHSTAR_STYLE"
    ))
    ext.set("year", "2019")
    exclude("**/build/*")
    exclude("**/.gradle/*")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation("org.spockframework:spock-core:2.0-groovy-3.0")
            }
        }

        create<JvmTestSuite>("functionalTest") {
            dependencies {
                implementation("org.spockframework:spock-core:2.0-groovy-3.0")
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        systemProperty("test.gradle-version", project.findProperty("test.gradle-version") ?: GradleVersion.current().version)
                    }
                }
            }
        }
    }
}

gradlePlugin.testSourceSets(sourceSets["functionalTest"])

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    check {
        dependsOn(testing.suites.named("functionalTest"))
    }
}
