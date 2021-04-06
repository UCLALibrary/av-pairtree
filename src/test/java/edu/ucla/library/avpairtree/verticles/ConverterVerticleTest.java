
package edu.ucla.library.avpairtree.verticles;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.AvPtConstants;
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

        final CsvItem csvItem = new CsvItem();

        // Create the CSV item we want the converter verticle to process
        csvItem.setItemARK("ark:/21198/zz002dvxmm");
        csvItem.setFilePath("soul/audio/uclapasc.wav");

        // Send a message with the CSV file location to the converter verticle
        vertx.eventBus().request(ConverterVerticle.class.getName(), csvItem).onSuccess(conversion -> {
            final CsvItem convertedItem = (CsvItem) conversion.body();
            final Path path = Path.of(AvPtConstants.SYSTEM_TMP_DIR, ConverterVerticle.SCRATCH_SPACE, "uclapasc.mp4");

            assertEquals(path.toAbsolutePath().toString(), convertedItem.getFilePath());
            complete(asyncTask);
        }).onFailure(error -> aContext.fail(error));
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
