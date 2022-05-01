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
package dev.jacomet.gradle.plugins.logging;

import dev.jacomet.gradle.plugins.logging.extension.LoggingCapabilitiesExtension;
import dev.jacomet.gradle.plugins.logging.rules.CommonsLoggingImplementationRule;
import dev.jacomet.gradle.plugins.logging.rules.Log4J2Alignment;
import dev.jacomet.gradle.plugins.logging.rules.Log4J2Implementation;
import dev.jacomet.gradle.plugins.logging.rules.Log4J2vsSlf4J;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JAlignment;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JImplementation;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JVsJCL;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JVsLog4J2ForJCL;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JvsJUL;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JvsLog4J;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JvsLog4J2ForJUL;
import dev.jacomet.gradle.plugins.logging.rules.Slf4JvsLog4J2ForLog4J;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RulesMode;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.util.GradleVersion;

public class LoggingCapabilitiesPlugin implements Plugin<Object> {

    private static final GradleVersion GRADLE_6_8 = GradleVersion.version("6.8");
    private static final GradleVersion GRADLE_6_2 = GradleVersion.version("6.2");
    private static final GradleVersion GRADLE_6 = GradleVersion.version("6.0");
    private static final GradleVersion GRADLE_5_2 = GradleVersion.version("5.2");
    public static final String SERVICE_NAME = "dev.ljacomet.logging-capabilities";

    @Override
    public void apply(Object target) {
        GradleVersion gradleVersion = GradleVersion.current();

        final Consumer<Action<? super ComponentMetadataHandler>> components;
        if (target instanceof Settings) {
            Settings settings = (Settings) target;
            settings.getGradle().getSharedServices().registerIfAbsent(SERVICE_NAME, LoggingCapabilitiesService.class, LoggingCapabilitiesService.Params.forSettings(settings));
            components = settings.getDependencyResolutionManagement()::components;
        } else if (target instanceof Project) {
            Project project = (Project) target;
            DependencyHandler dependencies = project.getDependencies();

            if (gradleVersion.compareTo(GRADLE_6) >= 0) {
                // Only add the extension for Gradle 6 and above
                project.getExtensions().create("loggingCapabilities", LoggingCapabilitiesExtension.class, project.getConfigurations(), dependencies, getAlignmentActivation(dependencies, gradleVersion));
            }

            if (gradleVersion.compareTo(GRADLE_6_8) >= 0) {
                // only configure component metadata rules if not already configured at Settings level
                // if using RulesMode.PREFER_PROJECT, accumulate rules and only actually "play" them if any rule is configured in project
                LoggingCapabilitiesService service = project.getGradle().getSharedServices().registerIfAbsent(SERVICE_NAME, LoggingCapabilitiesService.class, LoggingCapabilitiesService.Params::none).get();
		if (service.getRulesMode() == null) {
                    components = project.getDependencies()::components;
		} else if (service.getRulesMode() == RulesMode.PREFER_PROJECT) {
                    List<Action<? super ComponentMetadataHandler>> actions = new ArrayList<>();
                    components = actions::add;
                    ((ComponentMetadataHandlerInternal) project.getDependencies().getComponents()).onAddRule(ignored -> {
                        project.getLogger().lifecycle("I've been called! {}", ignored.getDisplayName());
                        actions.forEach(project.getDependencies()::components);
                    });
                } else {
                    components = action -> {};
                }
            } else {
                components = project.getDependencies()::components;
            }
        } else {
            throw new GradleException("The Gradle Enterprise plugin must be applied to the settings (was applied to " + (target instanceof Gradle ? "init script" : target.getClass().getName()) + ")");
        }

        configureCommonsLogging(components);
        configureJavaUtilLogging(components);
        configureLog4J(components);
        configureSlf4J(components);
        configureLog4J2(components);
        configureLog4J2Implementation(components);

        // ljacomet/logging-capabilities#4
        if (gradleVersion.compareTo(GRADLE_5_2) < 0 || gradleVersion.compareTo(GRADLE_6_2) >= 0) {
            configureAlignment(components);
        }
    }

    private Runnable getAlignmentActivation(DependencyHandler dependencies, GradleVersion gradleVersion) {
        if (gradleVersion.compareTo(GRADLE_6_2) < 0) {
            return () -> {
                configureAlignment(dependencies::components);
            };
        }
        return () -> {};
    }

    private void configureAlignment(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept(handler -> {
            handler.all(Slf4JAlignment.class);
            handler.all(Log4J2Alignment.class);
        });
    }

    /**
     * Log4J2 can act as an Slf4J implementation with `log4j-slf4j-impl`.
     * It can also delegate to Slf4J with `log4j-to-slf4j`.
     *
     * Given the above:
     * * `log4j-slf4j-impl` and `log4j-to-slf4j` are exclusive
     */
    private void configureLog4J2(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept(handler -> {
            handler.withModule(LoggingModuleIdentifiers.LOG4J_SLF4J_IMPL.moduleId, Log4J2vsSlf4J.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J_TO_SLF4J.moduleId, Log4J2vsSlf4J.class);
        });
    }

    /**
     * Log4J2 has its own implementation with `log4j-core`.
     * It can also delegate to Slf4J with `log4j-to-slf4j`.
     * <p>
     * Given the above:
     * * `log4j-core` and `log4j-to-slf4j` are exclusive
     */
    private void configureLog4J2Implementation(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept(handler -> {
            handler.withModule(LoggingModuleIdentifiers.LOG4J_TO_SLF4J.moduleId, Log4J2Implementation.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J_CORE.moduleId, Log4J2Implementation.class);
        });
    }

    /**
     * Slf4J provides an API, which requires an implementation.
     * Only one implementation can be on the classpath, selected between:
     * * `slf4j-simple`
     * * `logback-classic`
     * * `slf4j-log4j12` to use Log4J 1.2
     * * `slf4j-jcl` to use Jakarta Commons Logging
     * * `slf4j-jdk14` to use Java Util Logging
     * * `log4j-slf4j-impl` to use Log4J2
     */
    private void configureSlf4J(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept(handler -> {
            handler.withModule(LoggingModuleIdentifiers.SLF4J_SIMPLE.moduleId, Slf4JImplementation.class);
            handler.withModule(LoggingModuleIdentifiers.LOGBACK_CLASSIC.moduleId, Slf4JImplementation.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_LOG4J12.moduleId, Slf4JImplementation.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_JCL.moduleId, Slf4JImplementation.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_JDK14.moduleId, Slf4JImplementation.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J_SLF4J_IMPL.moduleId, Slf4JImplementation.class);
        });
    }

    /**
     * `log4j:log4j` can be replaced by:
     * * Slf4j with `log4j-over-slf4j`
     * * Log4J2 with `log4j-1.2-api`
     *
     * Log4J can be used from:
     * * Slf4J API delegating to it with `slf4j-log4j12`
     * * Log4J2 API only through Slf4J delegation
     *
     * Given the above:
     * * `log4j-over-slf4j` and `slf4j-log4j12` are exclusive
     * * `log4j-over-slf4j` and `log4j-1.2-api` and `log4j` are exclusive
     */
    private void configureLog4J(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept(handler -> {
            handler.withModule(LoggingModuleIdentifiers.LOG4J_OVER_SLF4J.moduleId, Slf4JvsLog4J.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_LOG4J12.moduleId, Slf4JvsLog4J.class);

            handler.withModule(LoggingModuleIdentifiers.LOG4J_OVER_SLF4J.moduleId, Slf4JvsLog4J2ForLog4J.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J12API.moduleId, Slf4JvsLog4J2ForLog4J.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J.moduleId, Slf4JvsLog4J2ForLog4J.class);
        });
    }

    /**
     * Java Util Logging can be replaced by:
     * * Slf4J with `jul-to-slf4j`
     * * Log4J2 with `log4j-jul`
     *
     * Java Util Logging can be used from:
     * * Slf4J API delegating to it with `slf4j-jdk14`
     * * Log4J2 API only through SLF4J delegation
     *
     * Given the above:
     * * `jul-to-slf4j` and `slf4j-jdk14` are exclusive
     * * `jul-to-slf4j` and `log4j-jul` are exclusive
     */
    private void configureJavaUtilLogging(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept( handler -> {
            handler.withModule(LoggingModuleIdentifiers.JUL_TO_SLF4J.moduleId, Slf4JvsJUL.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_JDK14.moduleId, Slf4JvsJUL.class);

            handler.withModule(LoggingModuleIdentifiers.JUL_TO_SLF4J.moduleId, Slf4JvsLog4J2ForJUL.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J_JUL.moduleId, Slf4JvsLog4J2ForJUL.class);
        });
    }

    /**
     * `commons-logging:commons-logging` can be replaced by:
     * * Slf4J with `org.slf4j:jcl-over-slf4j`
     * * Log4J2 with `org.apache.logging.log4j:log4j-jcl` _which requires `commons-logging`_
     *
     * `commons-logging:commons-logging` can be used from:
     * * Slf4J API delegating to it with `org.slf4j:slf4j-jcl`
     * * Log4J2 API only through Slf4J delegation
     *
     * Given the above:
     * * `jcl-over-slf4j` and `slf4j-jcl` are exclusive
     * * `commons-logging` and `jcl-over-slf4j` are exclusive
     * * `jcl-over-slf4j` and `log4j-jcl` are exclusive
     */
    private void configureCommonsLogging(Consumer<Action<? super ComponentMetadataHandler>> components) {
        components.accept( handler -> {
            handler.withModule(LoggingModuleIdentifiers.COMMONS_LOGGING.moduleId, CommonsLoggingImplementationRule.class);
            handler.withModule(LoggingModuleIdentifiers.JCL_OVER_SLF4J.moduleId, CommonsLoggingImplementationRule.class);

            handler.withModule(LoggingModuleIdentifiers.JCL_OVER_SLF4J.moduleId, Slf4JVsJCL.class);
            handler.withModule(LoggingModuleIdentifiers.SLF4J_JCL.moduleId, Slf4JVsJCL.class);

            handler.withModule(LoggingModuleIdentifiers.JCL_OVER_SLF4J.moduleId, Slf4JVsLog4J2ForJCL.class);
            handler.withModule(LoggingModuleIdentifiers.LOG4J_JCL.moduleId, Slf4JVsLog4J2ForJCL.class);
        });
    }
}
