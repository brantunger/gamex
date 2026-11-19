package io.github.brantunger.gamex.desktop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

class BuildPackagingConfigTest {

    private static String readDesktopBuildScript() throws IOException {
        Path fromRoot = Paths.get("desktop", "build.gradle");
        if (Files.isRegularFile(fromRoot)) {
            return Files.readString(fromRoot, StandardCharsets.UTF_8);
        }
        Path fromDesktop = Paths.get("build.gradle");
        if (Files.isRegularFile(fromDesktop)) {
            return Files.readString(fromDesktop, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Could not locate desktop/build.gradle from working directory");
    }

    @Test
    void applicationDefaultJvmArgsMatchStartupHelperJvmFlags() throws IOException {
        String script = readDesktopBuildScript();
        assertTrue(script.contains("applicationDefaultJvmArgs"),
            "desktop/build.gradle must configure applicationDefaultJvmArgs for startScripts and run");
        assertTrue(script.contains("'" + StartupHelper.ENABLE_NATIVE_ACCESS_ARG + "'")
                || script.contains("\"" + StartupHelper.ENABLE_NATIVE_ACCESS_ARG + "\""),
            "applicationDefaultJvmArgs must include " + StartupHelper.ENABLE_NATIVE_ACCESS_ARG);
        assertTrue(script.contains("'" + StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG + "'")
                || script.contains("\"" + StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG + "\""),
            "applicationDefaultJvmArgs must include " + StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG);
    }

    @Test
    void fatJarExcludesRootAndMultiReleaseModuleInfoDescriptors() throws IOException {
        String script = readDesktopBuildScript();
        assertTrue(script.contains("'module-info.class'") || script.contains("\"module-info.class\"")
                || script.contains("'**/module-info.class'"),
            "configureFatJar must exclude root module-info.class");
        assertTrue(script.contains("'META-INF/versions/**/module-info.class'")
                || script.contains("\"META-INF/versions/**/module-info.class\"")
                || script.contains("'**/module-info.class'"),
            "configureFatJar must exclude Multi-Release META-INF/versions/**/module-info.class");
    }

    @Test
    void mainDistributionExcludesLooseRuntimeClasspathJarsViaPropagatingEachFile() throws IOException {
        String script = readDesktopBuildScript();
        Pattern propagatingEachFile = Pattern.compile(
            "distributions\\s*\\{[\\s\\S]*?main\\s*\\{[\\s\\S]*?contents\\s*\\{[\\s\\S]*?eachFile\\s*\\{[\\s\\S]*?distRuntimeClasspath\\.contains\\([\\s\\S]*?\\.exclude\\(\\)");
        assertTrue(propagatingEachFile.matcher(script).find(),
            "distributions.main.contents must exclude distRuntimeClasspath files via eachFile { ... exclude() }");
        Pattern brokenPredicateExclude = Pattern.compile(
            "contents\\s*\\{\\s*exclude\\s*\\{\\s*distRuntimeClasspath\\.contains");
        assertFalse(brokenPredicateExclude.matcher(script).find(),
            "root predicate exclude { distRuntimeClasspath.contains(...) } does not propagate to into('lib')");
    }
}
