package io.github.brantunger.gamex.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class StartupHelperTest {

    @Test
    void restartsOnlyOnMacOs() {
        assertTrue(StartupHelper.needsRestart("Mac OS X", null, null));
        assertTrue(StartupHelper.needsRestart("mac os x", "", null));
        assertFalse(StartupHelper.needsRestart("Windows 11", null, null));
        assertFalse(StartupHelper.needsRestart("Linux", null, null));
        assertFalse(StartupHelper.needsRestart(null, null, null));
    }

    @Test
    void neverRestartsANativeImageOrARestartedJvm() {
        assertFalse(StartupHelper.needsRestart("Mac OS X", "runtime", null));
        assertFalse(StartupHelper.needsRestart("Mac OS X", null, "true"));
        assertTrue(StartupHelper.needsRestart("Mac OS X", null, "false"));
    }

    @Test
    void childCommandStartsOnFirstThreadAndKeepsJvmAndProgramArguments() {
        List<String> command = StartupHelper.childCommand("/jdk/bin/java",
            Arrays.asList("--enable-native-access=ALL-UNNAMED", "-Xmx1G"), "gamex.jar",
            DesktopLauncher.class.getName(), new String[] {"one", "two"});
        assertEquals(Arrays.asList("/jdk/bin/java", "-XstartOnFirstThread",
            "-D" + StartupHelper.JVM_RESTARTED_PROPERTY + "=true", "--enable-native-access=ALL-UNNAMED", "-Xmx1G",
            "-cp", "gamex.jar", "io.github.brantunger.gamex.desktop.DesktopLauncher", "one", "two"), command);
    }

    @Test
    void childCommandAcceptsNoProgramArguments() {
        List<String> command = StartupHelper.childCommand("java", Collections.emptyList(), "cp", "Main", null);
        assertEquals(Arrays.asList("java", "-XstartOnFirstThread",
            "-D" + StartupHelper.JVM_RESTARTED_PROPERTY + "=true", "-cp", "cp", "Main"), command);
    }
}
