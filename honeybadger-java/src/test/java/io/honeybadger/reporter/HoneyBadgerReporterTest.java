package io.honeybadger.reporter;

import io.honeybadger.reporter.config.ConfigContext;
import io.honeybadger.reporter.config.SystemSettingsConfigContext;
import org.apache.http.HttpVersion;
import org.apache.http.client.fluent.FakeResponse;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.assertEquals;


public class HoneyBadgerReporterTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    class ExceptionThrowingReporter extends HoneybadgerReporter {
        int attemptCount = 0;

        public ExceptionThrowingReporter(ConfigContext configContext) {
            super(configContext);
        }

        @Override
        protected Response sendToHoneybadger(final String jsonError) throws IOException {
            attemptCount = attemptCount + 1;
            logger.info("We Tried: " + attemptCount);

            throw new IOException("staged IO exception");
        }
    }

    class BadResponseGivingReporter extends HoneybadgerReporter {
        int attemptCount = 0;

        public BadResponseGivingReporter(ConfigContext configContext) {
            super(configContext);
        }

        @Override
        protected Response sendToHoneybadger(final String jsonError) throws IOException {
            attemptCount = attemptCount + 1;
            logger.info("We Tried: " + attemptCount);
            return new FakeResponse(
                    new DefaultHttpResponseFactory().newHttpResponse(
                        HttpVersion.HTTP_1_1,
                        500,
                        new BasicHttpContext()
                    )
            );
        }
    }

    @Test
    public void retriesUpTo3TimesWithDefaultConfig() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy");
        ExceptionThrowingReporter reporter = new ExceptionThrowingReporter(config);
        reporter.reportError(new Exception("Always fail"));
        assertEquals(4, reporter.attemptCount);
        assertEquals(3, (long)config.getMaximumErrorReportingRetries());
    }

    @Test
    public void retriesUpTo5TimesWhenConfigured() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy")
                .getMaximumErrorReportingRetries(5);
        ExceptionThrowingReporter reporter = new ExceptionThrowingReporter(config);
        reporter.reportError(new Exception("Always fail"));
        assertEquals(6, reporter.attemptCount);
        assertEquals(5, (long)config.getMaximumErrorReportingRetries());
    }

    @Test
    public void neverRetriesWithConfigurationValue0() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy")
                .getMaximumErrorReportingRetries(0);
        ExceptionThrowingReporter reporter = new ExceptionThrowingReporter(config);
        reporter.reportError(new Exception("Always fail"));
        assertEquals(1, reporter.attemptCount);
        assertEquals(0, (long)config.getMaximumErrorReportingRetries());
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void rejectsMaximumAttemptValuesLessThan0() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy")
                .getMaximumErrorReportingRetries(-1);

        thrown.expect(IllegalArgumentException.class);

        new ExceptionThrowingReporter(config);
    }

    @Test
    public void retriesWithUnexpectedHttp500Response() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy");
        BadResponseGivingReporter reporter = new BadResponseGivingReporter(config);
        reporter.reportError(new Exception("Always fail"));
        assertEquals(4, reporter.attemptCount);
    }

    @Test
    public void neverRetriesWithConfigurationValue0Http500Response() throws Exception {
        ConfigContext config = new SystemSettingsConfigContext().setApiKey("dummy")
                .getMaximumErrorReportingRetries(0);
        BadResponseGivingReporter reporter = new BadResponseGivingReporter(config);
        reporter.reportError(new Exception("Always fail"));
        assertEquals(1, reporter.attemptCount);
        assertEquals(0, (long)config.getMaximumErrorReportingRetries());
    }
}
