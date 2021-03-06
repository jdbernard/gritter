import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import static ch.qos.logback.classic.Level.*

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%level - %msg%n"
    }
}

appender("FILE", FileAppender) {
    file="gritter.log"
    encoder(PatternLayoutEncoder) {
        pattern = "%date %level %logger - %msg%n"
    }
}

root(WARN, ["CONSOLE"])
logger("com.jdbernard.twitter", TRACE, ["FILE"], false)
