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
package dev.jacomet.gradle.plugins.logging

import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED

class LoggingCapabilitiesPluginDetectionFunctionalTest extends AbstractLoggingCapabilitiesPluginFunctionalTest {

    @Unroll
    def "can detect Slf4J logger implementation conflicts with #first and #second"() {
        given:
        withBuildScriptWithDependencies(first, second)

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:slf4j-impl:1.0'")
        }

        where:
        first                           | second
        'org.slf4j:slf4j-simple:1.7.27' | 'ch.qos.logback:logback-classic:1.2.3'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-log4j12:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-jcl:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-jdk14:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.0'

    }

    @Unroll
    def "can detect Slf4J logger implementation / bridge implementation conflicts with #first and #second"() {
        given:
        withBuildScriptWithDependencies(first, second)

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:$capability:1.7.27'")
        }

        where:
        first                               | second                            | capability
        'org.slf4j:jcl-over-slf4j:1.7.27'   | 'org.slf4j:slf4j-jcl:1.7.27'      | 'slf4j-vs-jcl'
        'org.slf4j:jul-to-slf4j:1.7.27'     | 'org.slf4j:slf4j-jdk14:1.7.27'    | 'slf4j-vs-jul'
        'org.slf4j:log4j-over-slf4j:1.7.27' | 'org.slf4j:slf4j-log4j12:1.7.27'  | 'slf4j-vs-log4j'
    }

    @Unroll
    def "can detect Slf4J bridge implementations vs native logger implementations with #first and #second"() {
        given:
        withBuildScriptWithDependencies(first, second)

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:$capability:1.0'")
        }

        where:
        first                                           | second                                            | capability
        'org.slf4j:jcl-over-slf4j:1.7.27'               | 'commons-logging:commons-logging:1.2'             | 'commons-logging-impl'
        'org.slf4j:log4j-over-slf4j:1.7.27'             | 'log4j:log4j:1.2.9'                               | 'slf4j-vs-log4j2-log4j'
        'org.slf4j:log4j-over-slf4j:1.7.27'             | 'org.apache.logging.log4j:log4j-1.2-api:2.17.0'   | 'slf4j-vs-log4j2-log4j'
        'org.slf4j:slf4j-log4j12:1.7.27'                | 'org.apache.logging.log4j:log4j-1.2-api:2.17.0'   | 'slf4j-vs-log4j2-log4j'
        'org.apache.logging.log4j:log4j-1.2-api:2.17.0' | 'org.slf4j:slf4j-log4j12:1.7.27'                  | 'slf4j-vs-log4j2-log4j'
    }

    def "can detect Log4J2 logger implementation / bridge implementation conflict"() {
        given:
        withBuildScriptWithDependencies('org.apache.logging.log4j:log4j-slf4j-impl:2.17.0', 'org.apache.logging.log4j:log4j-to-slf4j:2.17.0')

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:log4j2-vs-slf4j:2.17.0'")
        }
    }

    def "can detect Log4J2 logger implementation conflict"() {
        given:
        withBuildScriptWithDependencies('org.apache.logging.log4j:log4j-core:2.17.0', 'org.apache.logging.log4j:log4j-to-slf4j:2.17.0')

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:log4j2-impl:2.17.0")
        }
    }

    @Unroll
    def "can detect conflicting bridge implementations from Slf4J and Log4J2 with #first and #second"() {
        given:
        withBuildScriptWithDependencies(first, second)

        expect:
        buildAndFail(['doIt']) {
            assert outcomeOf(delegate, ':doIt') == FAILED
            assert output.contains("conflict on capability 'dev.jacomet.logging:$capability:1.0'")
        }

        where:
        first                               | second                                        | capability
        'org.slf4j:jul-to-slf4j:1.7.27'     | 'org.apache.logging.log4j:log4j-jul:2.17.0'   | 'slf4j-vs-log4j2-jul'
        'org.slf4j:jcl-over-slf4j:1.7.27'   | 'org.apache.logging.log4j:log4j-jcl:2.17.0'   | 'slf4j-vs-log4j2-jcl'
    }

    def "provides alignment on Slf4J"() {
        given:
        withBuildScriptWithDependencies("org.slf4j:slf4j-simple:1.7.25", "org.slf4j:slf4j-api:1.7.27")
        withBuildScript("""
loggingCapabilities {
    enableAlignment()
}
""")

        when:
        def result = build(['doIt'])

        then:
        result.output.contains("slf4j-simple-1.7.27.jar")
    }

    def "provides alignment on Log4J 2"() {
        given:
        withBuildScriptWithDependencies("org.apache.logging.log4j:log4j-to-slf4j:2.17.0", "org.apache.logging.log4j:log4j-api:2.16.0")
        withBuildScript("""
loggingCapabilities {
    enableAlignment()
}
""")

        when:
        def result = build(['doIt'])

        then:
        result.output.contains("log4j-to-slf4j-2.17.0.jar")
    }
}