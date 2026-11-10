rootProject.name = "antidupe"

include("core")
// The Paper plugin module needs repo.papermc.io and Java 25; enable with -Pwith-plugin.
if (providers.gradleProperty("with-plugin").isPresent) include("plugin")
