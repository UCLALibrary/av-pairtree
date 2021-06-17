package edu.ucla.library.avpairtree.verticles;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests the waveform verticle.
 */
@RunWith(VertxUnitRunner.class)
public class WaveformVerticleTest extends AbstractAvPtTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformVerticleTest.class, MessageCodes.BUNDLE);

    /**
     * Tests the generation of audiowaveform data and its upload to S3.
     *
     * @param aContext A test context
     */
    @Test
    public void testWaveformGenerationAndS3Storage(final TestContext aContext) {

        LOGGER.debug(MessageCodes.AVPT_003, myNames.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();
        final CsvItem csvItem = new CsvItem();

        // Create the CSV item we want the waveform verticle to process
        csvItem.setItemARK("ark:/21198/zz002dvxmm");
        csvItem.setFilePath("soul/audio/uclapasc.wav");

        // TODO: mock the S3 bucket
        vertx.eventBus().<JsonObject>request(WaveformVerticle.class.getName(), csvItem).onSuccess(transformation -> {
            // Compare the data retrieved from S3 with a local test fixture
            final String audiowaveformURL = transformation.body().getString(csvItem.getItemARK());

            WebClient.create(vertx).getAbs(audiowaveformURL).send().onSuccess(resp -> {
                final Buffer expected =
                        vertx.fileSystem().readFileBlocking("src/test/resources/soul/audio/uclapasc.dat");
                final Buffer actual = resp.body();

                try {
                    assertEquals(expected, actual);
                } catch (final AssertionError details) {
                    LOGGER.error(details, details.getMessage());
                    aContext.fail();
                } finally {
                    // TODO: clean up the S3 bucket
                    asyncTask.complete();
                }
            }).onFailure(error -> {
                LOGGER.error(error, error.getMessage());
                aContext.fail();
            });
        }).onFailure(error -> {
            LOGGER.error(error, error.getMessage());
            aContext.fail();
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
