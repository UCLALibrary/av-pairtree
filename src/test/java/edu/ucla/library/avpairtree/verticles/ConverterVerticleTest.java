
package edu.ucla.library.avpairtree.verticles;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests the converter verticle.
 */
@RunWith(VertxUnitRunner.class)
public class ConverterVerticleTest extends AbstractAvPtTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConverterVerticleTest.class, MessageCodes.BUNDLE);

    /**
     * Tests the watcher's CSV parsing and submission of audio conversion jobs.
     *
     * @param aContext A test context
     */
    @Test
    public void testWatcherAudioCsvParsing(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();

        // Replace the verticle that receives the watcher verticle's video output with our simple mock verticle
        undeployVerticle(PairtreeVerticle.class.getName()).onSuccess(undeploy -> {
            final CsvItem csvItem = new CsvItem();

            // Create the CSV item we want the converter verticle to process
            csvItem.setItemARK("ark:/21198/zz002dvxmm");
            csvItem.setFilePath("soul/audio/uclapasc.wav");

            vertx.eventBus().<CsvItem>consumer(PairtreeVerticle.class.getName()).handler(message -> {
                final CsvItem found = message.body();
                final String foundFilePath = found.getFilePath();

                assertTrue(foundFilePath.startsWith(System.getProperty("java.io.tmpdir")));
                assertTrue(foundFilePath.contains(ConverterVerticle.SCRATCH_SPACE_PREFIX));
                assertTrue(foundFilePath.endsWith("/uclapasc.mp4"));

                message.reply(found);
            });

            // Send a message with the CSV file location to the watcher verticle
            vertx.eventBus().request(ConverterVerticle.class.getName(), csvItem).onSuccess(conversion -> {
                complete(asyncTask);
            }).onFailure(error -> aContext.fail(error));
        }).onFailure(error -> aContext.fail(error));
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
