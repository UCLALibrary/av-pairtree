
package edu.ucla.library.avpairtree.verticles;

import static edu.ucla.library.avpairtree.AvPtConstants.SYSTEM_TMP_DIR;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests of the Pairtree verticle.
 */
@RunWith(VertxUnitRunner.class)
public class PairtreeVerticleTest extends AbstractAvPtTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PairtreeVerticleTest.class, MessageCodes.BUNDLE);

    private static final String PT_ROOT = "target/pairtree/{}/pairtree_root";

    /**
     * Tests the insertion of video files into the Pairtree.
     *
     * @param aContext A test context
     */
    @Test
    public void testVideoPairtreeObjectCreation(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();
        final CsvItem csvItem = new CsvItem();

        // Create the CSV item we want the Pairtree verticle to process
        csvItem.setItemARK("ark:/21198/zz002hdsj2");
        csvItem.setFilePath("synanon/video/synanon.mp4");
        csvItem.setPathRoot(csvItem.getFilePath());

        // Confirm the item exists in the Pairtree and that the response we get back says it was processed
        vertx.eventBus().<CsvItem>request(PairtreeVerticle.class.getName(), csvItem).onSuccess(response -> {
            final String ptRoot = StringUtils.format(PT_ROOT, "synanon");
            final String path = ptRoot + "/21/19/8=/zz/00/2h/ds/j2/21198=zz002hdsj2/ark+=21198=zz002hdsj2.mp4";

            assertTrue(vertx.fileSystem().existsBlocking(path));
            assertTrue(response.body().isProcessed());

            complete(asyncTask);
        }).onFailure(error -> LOGGER.error(error, error.getMessage()));
    }

    /**
     * Tests the insertion of audio files into the Pairtree.
     *
     * @param aContext A test context
     */
    @Test
    public void testAudioPairtreeObjectCreation(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();
        final CsvItem csvItem = new CsvItem();
        final FileSystem fileSystem = vertx.fileSystem();
        final String filePath =
            Path.of(SYSTEM_TMP_DIR, ConverterVerticle.SCRATCH_SPACE, "uclapasc.mp4").toAbsolutePath().toString();

        if (fileSystem.existsBlocking(filePath)) {
            fileSystem.deleteBlocking(filePath);
        }

        // Copy our test fixture to an absolute path (similar to what our converter does, but this doesn't convert)
        fileSystem.copyBlocking("src/test/resources/soul/audio/uclapasc.wav", filePath);

        // Create the CSV item we want the Pairtree verticle to process
        csvItem.setItemARK("ark:/21198/zz002dw148");
        csvItem.setFilePath(filePath); // This is a processed/converted path
        csvItem.setPathRoot("soul/audio/uclapasc.wav"); // This is the original path

        // Confirm the item exists in the Pairtree and that the response we get back says it was processed
        vertx.eventBus().<CsvItem>request(PairtreeVerticle.class.getName(), csvItem).onSuccess(response -> {
            final String ptRoot = StringUtils.format(PT_ROOT, "soul");
            final String path = ptRoot + "/21/19/8=/zz/00/2d/w1/48/21198=zz002dw148/ark+=21198=zz002dw148.mp4";

            assertTrue(vertx.fileSystem().existsBlocking(path));
            assertTrue(response.body().isProcessed());

            fileSystem.deleteBlocking(filePath);
            complete(asyncTask);
        }).onFailure(error -> LOGGER.error(error, error.getMessage()));
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
