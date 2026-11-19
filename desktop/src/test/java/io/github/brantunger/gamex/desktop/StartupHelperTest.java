package io.github.brantunger.gamex.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

class StartupHelperTest {

    private static final BooleanSupplier FAIL_IF_CALLED = () -> {
        throw new AssertionError("isMainThread must not be queried off macOS or inside a native image");
    };

    private static List<String> captureWarnings(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(StartupHelper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            action.run();
        } finally {
            logger.setAdditive(additive);
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
        List<String> warnings = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            assertEquals(Level.WARN, event.getLevel(), event.getFormattedMessage());
            warnings.add(event.getFormattedMessage());
        }
        return warnings;
    }

    @Test
    void restartsOnlyOnMacOsWithoutQueryingObjCRuntimeElsewhere() {
        assertTrue(StartupHelper.needsRestart("Mac OS X", null, null, () -> false));
        assertTrue(StartupHelper.needsRestart("mac os x", "", null, () -> false));
        assertFalse(StartupHelper.needsRestart("Windows 11", null, null, FAIL_IF_CALLED));
        assertFalse(StartupHelper.needsRestart("Linux", null, null, FAIL_IF_CALLED));
        assertFalse(StartupHelper.needsRestart(null, null, null, FAIL_IF_CALLED));
        assertFalse(StartupHelper.needsRestart("Mac OS X", "runtime", null, FAIL_IF_CALLED));
    }

    @Test
    void relaunchedMacChildOnMainThreadReturnsFalseSilentlyWithoutWarning() {
        List<String> relaunchedWarnings = captureWarnings(
            () -> assertFalse(StartupHelper.needsRestart("Mac OS X", null, "true", () -> true)));
        assertEquals(List.of(), relaunchedWarnings);

        List<String> directFirstThreadWarnings = captureWarnings(
            () -> assertFalse(StartupHelper.needsRestart("Mac OS X", null, null, () -> true)));
        assertEquals(List.of(), directFirstThreadWarnings);
    }

    @Test
    void nonFirstThreadMacJvmWarnsAndStopsOnlyWhenAlreadyRestarted() {
        List<String> freshWarnings = captureWarnings(
            () -> assertTrue(StartupHelper.needsRestart("Mac OS X", null, null, () -> false)));
        assertEquals(List.of(), freshWarnings);

        List<String> explicitFalseWarnings = captureWarnings(
            () -> assertTrue(StartupHelper.needsRestart("Mac OS X", null, "false", () -> false)));
        assertEquals(List.of(), explicitFalseWarnings);

        List<String> loopGuardWarnings = captureWarnings(
            () -> assertFalse(StartupHelper.needsRestart("Mac OS X", null, "true", () -> false)));
        assertEquals(
            List.of("StartupHelper was asked to restart a JVM it had already restarted; carrying on."),
            loopGuardWarnings);
    }

    @Test
    void childCommandAddsManifestEquivalentNativeAccessAndAddExportsWhenMissing() {
        List<String> command = StartupHelper.childCommand("java", Collections.emptyList(), "gamex.jar",
            DesktopLauncher.class.getName(), null);
        assertEquals(Arrays.asList(
            "java",
            "-XstartOnFirstThread",
            "-D" + StartupHelper.JVM_RESTARTED_PROPERTY + "=true",
            StartupHelper.ENABLE_NATIVE_ACCESS_ARG,
            StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG,
            "-cp",
            "gamex.jar",
            "io.github.brantunger.gamex.desktop.DesktopLauncher"
        ), command);

        List<String> nullJvmArgsCommand = StartupHelper.childCommand("java", null, "cp", "Main", new String[] {"a"});
        assertEquals(Arrays.asList(
            "java",
            "-XstartOnFirstThread",
            "-D" + StartupHelper.JVM_RESTARTED_PROPERTY + "=true",
            StartupHelper.ENABLE_NATIVE_ACCESS_ARG,
            StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG,
            "-cp",
            "cp",
            "Main",
            "a"
        ), nullJvmArgsCommand);
    }

    @Test
    void childCommandPreservesExistingFlagsWithoutDuplicatingNativeAccessOrAddExports() {
        List<String> partial = StartupHelper.childCommand("/jdk/bin/java",
            Arrays.asList(StartupHelper.ENABLE_NATIVE_ACCESS_ARG, "-Xmx1G"), "gamex.jar",
            DesktopLauncher.class.getName(), new String[] {"one", "two"});
        assertEquals(Arrays.asList(
            "/jdk/bin/java",
            "-XstartOnFirstThread",
            "-D" + StartupHelper.JVM_RESTARTED_PROPERTY + "=true",
            StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG,
            StartupHelper.ENABLE_NATIVE_ACCESS_ARG,
            "-Xmx1G",
            "-cp",
            "gamex.jar",
            "io.github.brantunger.gamex.desktop.DesktopLauncher",
            "one",
            "two"
        ), partial);

        List<String> bothPresent = StartupHelper.childCommand("/jdk/bin/java",
            Arrays.asList(StartupHelper.ENABLE_NATIVE_ACCESS_ARG, StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG, "-Xmx1G"),
            "gamex.jar", DesktopLauncher.class.getName(), new String[0]);
        assertEquals(1, Collections.frequency(bothPresent, StartupHelper.ENABLE_NATIVE_ACCESS_ARG));
        assertEquals(1, Collections.frequency(bothPresent, StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG));

        List<String> splitAddExports = StartupHelper.childCommand("/jdk/bin/java",
            Arrays.asList("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"),
            "gamex.jar", "Main", null);
        assertFalse(splitAddExports.contains(StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG));
        assertEquals(1, Collections.frequency(splitAddExports, "--add-exports"));

        List<String> otherModuleExport = StartupHelper.childCommand("/jdk/bin/java",
            Collections.singletonList("--add-exports=java.desktop/sun.awt=ALL-UNNAMED"),
            "gamex.jar", "Main", null);
        assertTrue(otherModuleExport.contains(StartupHelper.ADD_EXPORTS_INTERNAL_MISC_ARG));
        assertTrue(otherModuleExport.contains("--add-exports=java.desktop/sun.awt=ALL-UNNAMED"));
    }
}
