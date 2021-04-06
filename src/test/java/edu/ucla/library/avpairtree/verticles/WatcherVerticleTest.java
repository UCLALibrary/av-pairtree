
package edu.ucla.library.avpairtree.verticles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.AvPtConstants;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.utils.TestConstants;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests of the CSV file system watcher verticle.
 */
@RunWith(VertxUnitRunner.class)
public class WatcherVerticleTest extends AbstractAvPtTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatcherVerticleTest.class, MessageCodes.BUNDLE);

    private static final String OUT_EXT = ".out";

    private static final String CSV_EXT = ".csv";

    private static final String FILE_EXT = ".mp4";

    /**
     * Tests the watcher's CSV parsing and submission of video conversion jobs.
     *
     * @param aContext A test context
     */
    @Test
    public void testWatcherVideoCsvParsing(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();

        // Replace the verticle that receives the watcher verticle's audio output with our simple mock verticle
        undeployVerticle(PairtreeVerticle.class.getName()).onSuccess(result -> {
            final String csvFilePath = TestConstants.CSV_DIR + TestConstants.SYNANON;
            final int expectedUpdates = 1;

            vertx.eventBus().<CsvItem>consumer(PairtreeVerticle.class.getName()).handler(message -> {
                final CsvItem found = message.body();

                assertTrue(LOGGER.getMessage(MessageCodes.AVPT_004), isExpected("synanon/video/synanon.mp4", found));
                message.reply(found);
            });

            // Send a message with the CSV file location to the watcher verticle
            vertx.eventBus().request(WatcherVerticle.class.getName(), csvFilePath).onSuccess(request -> {
                checkOutput(csvFilePath.replace(CSV_EXT, OUT_EXT), expectedUpdates, aContext).onComplete(check -> {
                    if (check.succeeded()) {
                        complete(asyncTask);
                    } else {
                        aContext.fail(check.cause());
                    }
                });
            }).onFailure(error -> aContext.fail(error));
        }).onFailure(error -> aContext.fail(error));
    }

    /**
     * Tests the watcher's CSV parsing and submission of audio conversion jobs.
     *
     * @param aContext A test context
     */
    @Test
    public void testWatcherAudioCsvParsing(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final String csvFilePath = TestConstants.CSV_DIR + TestConstants.SOUL;
        final int expectedUpdates = 175; // There are 175 audio files in our CSV test fixture
        final Async asyncTask = aContext.async(expectedUpdates + 1); // Add one additional checkpoint
        final Vertx vertx = myContext.vertx();

        // Replace the verticles that receive the watcher verticle's audio output with our simple mock verticles
        undeployVerticles(PairtreeVerticle.class.getName(), ConverterVerticle.class.getName()).onSuccess(result -> {
            // Mock the audio file converter
            vertx.eventBus().<CsvItem>consumer(ConverterVerticle.class.getName()).handler(message -> {
                final CsvItem item = message.body();
                final String foundFilePath = item.getFilePath();
                final String fileName = new File(foundFilePath).getName();
                final String uuid = UUID.randomUUID().toString();
                final String bareFileName = FileUtils.stripExt(fileName);
                final String newFileName = bareFileName + "-" + uuid + FILE_EXT;
                final String newFilePath = Paths.get(AvPtConstants.SYSTEM_TMP_DIR, newFileName).toString();

                assertEquals("soul/audio/uclapasc.wav", foundFilePath);

                // Create the file for the PairtreeVerticle to store
                vertx.fileSystem().copyBlocking("src/test/resources/" + foundFilePath, newFilePath);
                item.setFilePath(newFilePath);
                message.reply(item);
            });

            // Mock the Pairtree verticle
            vertx.eventBus().<CsvItem>consumer(PairtreeVerticle.class.getName()).handler(message -> {
                final CsvItem found = message.body();

                assertTrue(LOGGER.getMessage(MessageCodes.AVPT_004), isExpected(FILE_EXT, found));

                message.reply(found);
                asyncTask.countDown();
            });

            // Send a message with the CSV file location to the watcher verticle
            vertx.eventBus().request(WatcherVerticle.class.getName(), csvFilePath).onSuccess(request -> {
                checkOutput(csvFilePath.replace(CSV_EXT, OUT_EXT), expectedUpdates, aContext).onComplete(check -> {
                    if (check.succeeded()) {
                        asyncTask.countDown(); // Should complete the test
                    } else {
                        aContext.fail(check.cause());
                    }
                });
            }).onFailure(error -> aContext.fail(error));
        }).onFailure(error -> aContext.fail(error));
    }

    /**
     * Gets the logger used for this class' tests.
     */
    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Checks the CSV output to ensure the right number of IIIF access URLs have been added.
     *
     * @param aFileName A CSV file name
     * @param aLatchCount An expected number of IIIF access URLs
     * @param aContext A test context
     * @return The result of the output file check
     */
    private Future<Void> checkOutput(final String aFileName, final int aLatchCount, final TestContext aContext) {
        final Promise<Void> promise = Promise.promise();

        try (LineNumberReader reader = new LineNumberReader(new BufferedReader(new FileReader(aFileName)))) {
            final String avServer = myTestAvServer.substring(0, myTestAvServer.indexOf("{") - 1);
            final AtomicInteger counter = new AtomicInteger(0);

            reader.lines().forEach(line -> {
                if (line.contains(avServer)) {
                    counter.incrementAndGet();
                }
            });

            // Check that our CSV was updated the expected number of times
            aContext.assertEquals(aLatchCount, counter.get());
        } catch (final Exception details) {
            promise.fail(details);
        }

        // Delete the test artifact after we've checked it to determine success
        myContext.vertx().fileSystem().delete(aFileName).onComplete(deletion -> {
            if (deletion.succeeded()) {
                promise.complete();
            } else {
                promise.fail(deletion.cause());
            }
        });

        return promise.future();
    }

    /**
     * Confirms the found item matches the expected item.
     *
     * @param aExpectedFile An expected file
     * @param aFound A found item
     * @return True if the items match; else, false
     */
    private boolean isExpected(final String aExpectedFilePath, final CsvItem aFound) {
        final String foundFilePath = aFound.getFilePath();
        final String foundARK = aFound.getItemARK();

        return foundFilePath.endsWith(aExpectedFilePath) && TestConstants.EXPECTED_ARKS.contains(foundARK);
    }
}
