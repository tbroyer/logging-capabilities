package dev.jacomet.gradle.plugins.logging;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RulesMode;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.api.services.BuildServiceParameters;

abstract class LoggingCapabilitiesService implements BuildService<LoggingCapabilitiesService.Params> {
  interface Params extends BuildServiceParameters {
    Property<RulesMode> getRulesMode();

    // XXX: those methods are declared here to avoid a reference to BuildServiceSpec in lambdas/anonymous classes in the Plugin
    // otherwise earlier versions of Gradle where the class doesn't exist cannot even apply the plugin.

    static Action<BuildServiceSpec<Params>> forSettings(Settings settings) {
        return spec -> spec.getParameters().getRulesMode().set(settings.getDependencyResolutionManagement().getRulesMode());
    }

    static void none(Object spec) {}
  }

  @Inject
  public LoggingCapabilitiesService() {}

  public RulesMode getRulesMode() {
    return getParameters().getRulesMode().getOrNull();
  }
}
