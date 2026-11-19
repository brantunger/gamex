package io.github.brantunger.gamex.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

/** Checks how {@code GAMEX_LOG_LEVEL} is read and that the game's {@code logback.xml} ends up at that level. */
class LogLevelTest {

    private String previousProperty;

    @BeforeEach
    void rememberProperty() {
        previousProperty = System.getProperty(LogLevel.PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (previousProperty == null) {
            System.clearProperty(LogLevel.PROPERTY);
        } else {
            System.setProperty(LogLevel.PROPERTY, previousProperty);
        }
    }

    /** @return the root level the game's own {@code logback.xml} gives when the variable holds {@code env} */
    private static Level rootLevelFor(String env) throws JoranException {
        LogLevel.install(env);
        URL config = LogLevelTest.class.getResource("/logback.xml");
        assertNotNull(config, "the game's logback.xml is on the class path");
        LoggerContext context = new LoggerContext();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(config);
            return context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel();
        } finally {
            context.stop();
        }
    }

    @Test
    void missingOrBlankLevelIsInfoWithoutAWarning() {
        for (String input : new String[] {null, "", "   "}) {
            assertEquals("INFO", LogLevel.resolve(input), "'" + input + "'");
            assertFalse(LogLevel.isRejected(input), "no warning for '" + input + "'");
        }
    }

    @Test
    void everyLevelIsReadWhateverItsCaseAndSpacing() {
        String[][] cases = {
            {"trace", "TRACE"}, {"debug", "DEBUG"}, {"info", "INFO"}, {"warn", "WARN"}, {"error", "ERROR"},
            {"DEBUG", "DEBUG"}, {"Debug", "DEBUG"}, {"dEbUg", "DEBUG"}, {" warn ", "WARN"}, {"\tError\n", "ERROR"}
        };
        for (String[] pair : cases) {
            assertEquals(pair[1], LogLevel.resolve(pair[0]), "'" + pair[0] + "'");
            assertFalse(LogLevel.isRejected(pair[0]), "no warning for '" + pair[0] + "'");
        }
    }

    @Test
    void anythingElseIsInfoWithAWarning() {
        for (String input : new String[] {"garbage", "verbose", "warning", "all", "off", "debug,info", "1", "de bug"}) {
            assertEquals("INFO", LogLevel.resolve(input), "'" + input + "'");
            assertTrue(LogLevel.isRejected(input), "a warning for '" + input + "'");
        }
    }

    @Test
    void installSetsThePropertyTheConfigurationReads() {
        assertEquals("DEBUG", LogLevel.install("debug"));
        assertEquals("DEBUG", System.getProperty(LogLevel.PROPERTY));
        assertEquals("INFO", LogLevel.install("garbage"));
        assertEquals("INFO", System.getProperty(LogLevel.PROPERTY));
    }

    @Test
    void theGamesConfigurationRunsAtTheLevelAskedFor() throws JoranException {
        assertEquals(Level.INFO, rootLevelFor(null));
        assertEquals(Level.DEBUG, rootLevelFor("debug"));
        assertEquals(Level.WARN, rootLevelFor("Warn"));
        assertEquals(Level.TRACE, rootLevelFor("TRACE"));
        assertEquals(Level.ERROR, rootLevelFor("error"));
    }

    @Test
    void aLevelThatIsNotOneRunsTheGamesConfigurationAtInfoNotDebug() throws JoranException {
        // Logback itself reads a level it does not know as DEBUG, which is why the value is checked first.
        assertEquals(Level.DEBUG, Level.toLevel("garbage"));
        assertEquals(Level.INFO, rootLevelFor("garbage"));
        assertEquals(Level.INFO, rootLevelFor("verbose"));
    }

    @Test
    void theConfigurationIsAtInfoWhenThePropertyWasNeverSet() throws JoranException {
        System.clearProperty(LogLevel.PROPERTY);
        URL config = LogLevelTest.class.getResource("/logback.xml");
        LoggerContext context = new LoggerContext();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(config);
            assertEquals(Level.INFO, context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
        } finally {
            context.stop();
        }
    }
}
