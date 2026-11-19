package io.github.brantunger.gamex;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects what one class logs while a test runs. The tests log at WARN, so this lowers the class's logger to the
 * level asked for, and {@link #close()} puts the logger back as it was.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final boolean previousAdditive;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type, Level level) {
        logger = (Logger) LoggerFactory.getLogger(type);
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        logger.setLevel(level);
        // The lines go to this capture alone, not on to the console as well.
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
    }

    /**
     * Starts collecting what {@code type} logs.
     * @param type the class whose logger to listen to
     * @param level the lowest level to collect
     * @return the capture; close it when the test is done
     */
    public static LogCapture of(Class<?> type, Level level) {
        return new LogCapture(type, level);
    }

    /**
     * @param level the level to pick
     * @return the formatted messages logged at exactly that level so far, in order
     */
    public List<String> messages(Level level) {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == level) messages.add(event.getFormattedMessage());
        }
        return messages;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setAdditive(previousAdditive);
        logger.setLevel(previousLevel);
    }
}
