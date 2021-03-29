
package edu.ucla.library.avpairtree.verticles;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.HTTP;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.utils.TestConstants;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests the main verticle of the Vert.x application.
 */
@RunWith(VertxUnitRunner.class)
public class MainVerticleTest extends AbstractAvPtTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticleTest.class, MessageCodes.BUNDLE);

    /**
     * Tests the server can start successfully.
     *
     * @param aContext A test context
     */
    @Test
    public void testThatTheServerIsStarted(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final WebClient client = WebClient.create(myContext.vertx());
        final Async asyncTask = aContext.async();

        // Try to connect to the status endpoint to see if it responds the way we'd expect
        client.get(myPort, TestConstants.INADDR_ANY, "/status").send(get -> {
            if (get.succeeded()) {
                aContext.assertEquals(HTTP.OK, get.result().statusCode());
                complete(asyncTask);
            } else {
                aContext.fail(get.cause());
            }
        });
    }

    /**
     * Tests that copying a CSV file to the watched directory triggers a message to be sent to the WatcherVerticle. The
     * functionality of the directory watcher is tested in the WatcherVerticleTest class.
     *
     * @param aContext A test context
     */
    @Test
    public void testWatcherSetup(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();

        // Replace the receiving verticle with our mock verticle
        undeployVerticle(WatcherVerticle.class.getName()).onSuccess(result -> {
            final String watchedFile = getUniqueFileName(TestConstants.WATCHED_DIR + TestConstants.SYNANON);
            final Promise<Void> promise = Promise.promise();
            final FileSystem fileSystem = vertx.fileSystem();

            // Setup a consumer for our test that moves a CSV file into the watched folder
            vertx.eventBus().<String>consumer(WatcherVerticle.class.getName()).handler(message -> {
                promise.complete(); // Cleanup test resources after we've received the notification
                aContext.assertEquals(new File(watchedFile).getAbsolutePath(), message.body());
                complete(asyncTask);
            });

            // Copy a test CSV file into the watched folder so we can check the message that's sent
            fileSystem.copy(TestConstants.CSV_DIR + TestConstants.SYNANON, watchedFile, copy -> {
                if (copy.succeeded()) {
                    promise.future().onSuccess(cleanup -> {
                        fileSystem.delete(watchedFile).onFailure(error -> aContext.fail(error));
                    });
                } else {
                    aContext.fail(copy.cause());
                }
            });
        }).onFailure(error -> aContext.fail(error));
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
