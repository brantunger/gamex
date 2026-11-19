/*
 * Copyright 2020 damios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Note, the above license and copyright applies to this file only.
// Adapted from gdx-liftoff's StartupHelper (https://github.com/libgdx/gdx-liftoff), trimmed to the macOS utility.
package io.github.brantunger.gamex.desktop;

import org.lwjgl.system.JNI;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Relaunches the game on macOS so that it runs on the process's first thread.
 * <p>
 * GLFW, and so LWJGL3, must run on the first thread of the process on macOS, which a JVM only does when started with
 * {@code -XstartOnFirstThread}. The Gradle {@code run} task passes that option, but {@code java -jar} and a
 * double-clicked JAR do not, and GLFW then fails with an {@link IllegalStateException} before a window opens. When the
 * game is not on the first thread, this spawns a child JVM with the option, the same class path, JVM arguments and
 * program arguments, waits for it, and reports its exit code.
 * <p>
 * Every other OS, a JVM already on the first thread (such as one started by the Construo {@code .app} bundle or the
 * Gradle {@code run} task) and a Graal native image are left alone.
 * <a href="https://jvm-gaming.org/t/starting-jvm-on-mac-with-xstartonfirstthread-programmatically/57547">Based on
 * this java-gaming.org post by kappa</a>
 * @author damios
 */
final class StartupHelper {

    private static final Logger log = LoggerFactory.getLogger(StartupHelper.class);

    static final String JVM_RESTARTED_PROPERTY = "gamex.jvmIsRestarted";
    static final String ENABLE_NATIVE_ACCESS_ARG = "--enable-native-access=ALL-UNNAMED";
    static final String ADD_EXPORTS_INTERNAL_MISC_ARG = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED";

    private StartupHelper() {}

    /**
     * Starts a child JVM when the game needs one to run on macOS.
     * <pre><code>
     * int exitCode = StartupHelper.startNewJvmIfRequired(args);
     * if (exitCode != StartupHelper.NOT_RESTARTED) System.exit(exitCode);
     * </code></pre>
     * @param args the program arguments, passed on to the child JVM
     * @return {@link #NOT_RESTARTED} when this JVM should go on to start the game itself, or else the exit code of
     *     the child JVM that ran it
     */
    static int startNewJvmIfRequired(String[] args) {
        if (!needsRestart(System.getProperty("os.name"), System.getProperty("org.graalvm.nativeimage.imagecode"),
            System.getProperty(JVM_RESTARTED_PROPERTY), StartupHelper::isMainThread)) {
            return NOT_RESTARTED;
        }

        String javaExecPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (!new File(javaExecPath).exists()) {
            log.warn("A Java installation could not be found to restart with -XstartOnFirstThread. If you"
                + " are distributing this game with a bundled JRE, set the '-XstartOnFirstThread' argument manually.");
            return NOT_RESTARTED;
        }

        List<String> command = childCommand(javaExecPath,
            ManagementFactory.getRuntimeMXBean().getInputArguments(),
            System.getProperty("java.class.path"), DesktopLauncher.class.getName(), args);
        try {
            return new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (Exception e) {
            log.error("There was a problem restarting the JVM with -XstartOnFirstThread.", e);
            return 1;
        }
    }

    /** Returned by {@link #startNewJvmIfRequired(String[])} when no child JVM was started. */
    static final int NOT_RESTARTED = Integer.MIN_VALUE;

    /**
     * Decides whether a restart is needed. {@code os.name} and Graal native-image properties are checked before
     * invoking {@code isMainThread} so non-macOS JVMs never load the Objective-C runtime; {@code isMainThread} is
     * checked before {@code restartedFlag} so a relaunched macOS child JVM already on the first thread returns
     * {@code false} silently without printing a fallback warning.
     * @param osName the {@code os.name} property
     * @param graalImageCode the {@code org.graalvm.nativeimage.imagecode} property, or null
     * @param restartedFlag the {@value #JVM_RESTARTED_PROPERTY} property, or null
     * @param isMainThread lazy check for whether the calling thread is already the macOS first thread
     * @return true only on macOS, outside a native image, when not already on the first thread and not already restarted
     */
    static boolean needsRestart(String osName, String graalImageCode, String restartedFlag,
                                BooleanSupplier isMainThread) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("mac")) return false;
        if (graalImageCode != null && !graalImageCode.isEmpty()) return false;
        if (isMainThread.getAsBoolean()) return false;
        if ("true".equals(restartedFlag)) {
            // Unreachable when -XstartOnFirstThread worked; stops a child from spawning children forever if it did not.
            log.warn("StartupHelper was asked to restart a JVM it had already restarted; carrying on.");
            return false;
        }
        return true;
    }

    /** Builds the command line of the child JVM, preserving required JDK 24+ native-access and module-export flags. */
    static List<String> childCommand(String javaExecPath, List<String> jvmArgs, String classPath, String mainClass,
                                     String[] args) {
        List<String> safeJvmArgs = jvmArgs == null ? Collections.emptyList() : jvmArgs;
        List<String> command = new ArrayList<>();
        command.add(javaExecPath);
        command.add("-XstartOnFirstThread");
        command.add("-D" + JVM_RESTARTED_PROPERTY + "=true");
        if (!hasEnableNativeAccess(safeJvmArgs)) {
            command.add(ENABLE_NATIVE_ACCESS_ARG);
        }
        if (!hasAddExportsInternalMisc(safeJvmArgs)) {
            command.add(ADD_EXPORTS_INTERNAL_MISC_ARG);
        }
        command.addAll(safeJvmArgs);
        command.add("-cp");
        command.add(classPath);
        command.add(mainClass);
        if (args != null) command.addAll(Arrays.asList(args));
        return command;
    }

    private static boolean hasEnableNativeAccess(List<String> jvmArgs) {
        for (String arg : jvmArgs) {
            if (ENABLE_NATIVE_ACCESS_ARG.equals(arg)) return true;
        }
        return false;
    }

    private static boolean hasAddExportsInternalMisc(List<String> jvmArgs) {
        for (int i = 0; i < jvmArgs.size(); i++) {
            String arg = jvmArgs.get(i);
            if (ADD_EXPORTS_INTERNAL_MISC_ARG.equals(arg)) return true;
            if ("--add-exports".equals(arg) && i + 1 < jvmArgs.size()
                && "java.base/jdk.internal.misc=ALL-UNNAMED".equals(jvmArgs.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    /** @return whether the calling thread is the macOS main (first) thread; asks NSThread through the ObjC runtime */
    private static boolean isMainThread() {
        long objcMsgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        long nsThread = ObjCRuntime.objc_getClass("NSThread");
        long currentThread = JNI.invokePPP(nsThread, ObjCRuntime.sel_getUid("currentThread"), objcMsgSend);
        return JNI.invokePPZ(currentThread, ObjCRuntime.sel_getUid("isMainThread"), objcMsgSend);
    }
}
