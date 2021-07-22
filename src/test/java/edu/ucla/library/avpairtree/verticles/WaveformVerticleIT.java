package edu.ucla.library.avpairtree.verticles;

import static edu.ucla.library.avpairtree.AvPtConstants.WAVEFORM_CONSUMER;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.CsvItemCodec;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.handlers.AmazonS3WaveformConsumer;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests the waveform verticle.
 */
@RunWith(VertxUnitRunner.class)
public class WaveformVerticleIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformVerticleIT.class, MessageCodes.BUNDLE);

    private static final String CONSUMER_MOCK = "mock";

    private static final String DEPLOYMENT_ID = "deploymentID";

    @Rule
    public RunTestOnContext myContext = new RunTestOnContext();

    @Rule
    public TestName myTestName = new TestName();

    /**
     * Registers a waveform consumer that uses Localstack S3, creates a S3 bucket for testing, and deploys a
     * WaveformVerticle.
     *
     * @param aContext A test context
     */
    @Before
    public void setUp(final TestContext aContext) {
        final Async asyncTask = aContext.async();

        ConfigRetriever.create(myContext.vertx()).getConfig().compose(config -> {
            final DeploymentOptions options = new DeploymentOptions().setConfig(config);
            final AmazonS3WaveformConsumer localstack = new AmazonS3WaveformConsumer(config);
            final MessageConsumer<byte[]> waveformConsumer =
                    myContext.vertx().eventBus().consumer(WAVEFORM_CONSUMER, localstack);

            aContext.<AmazonS3WaveformConsumer>put(WAVEFORM_CONSUMER, localstack);
            aContext.<MessageConsumer<byte[]>>put(CONSUMER_MOCK, waveformConsumer);

            myContext.vertx().eventBus().registerDefaultCodec(CsvItem.class, new CsvItemCodec());

            // Return a chained Future that resolves with the WaveformVerticle's deployment id
            // Note that the S3 bucket will be cleaned up and deleted when the Localstack container is destroyed
            return localstack.createBucket().compose(create -> {
                return myContext.vertx().deployVerticle(WaveformVerticle.class, options);
            });
        }).onSuccess(deploymentID -> {
            aContext.put(DEPLOYMENT_ID, deploymentID);
            asyncTask.complete();
        }).onFailure(error -> aContext.fail(error));

    }

    /**
     * Unregisters the mock, undeploys the Waveform verticle, and deletes the S3 bucket.
     *
     * @param aContext A test context
     */
    @After
    public void tearDown(final TestContext aContext) {
        final Handler<AsyncResult<CompositeFuture>> asyncTaskHandler = aContext.asyncAssertSuccess();
        @SuppressWarnings("rawtypes")
        final List<Future> undeploys = new ArrayList<>();

        undeploys.add(aContext.<MessageConsumer<byte[]>>get(CONSUMER_MOCK).unregister());
        undeploys.add(myContext.vertx().undeploy(aContext.get(DEPLOYMENT_ID)));

        CompositeFuture.all(undeploys).onComplete(asyncTaskHandler);
    }

    /**
     * Tests the generation of audiowaveform data and its upload to S3.
     *
     * @param aContext A test context
     */
    @Test
    public void testWaveformGenerationAndS3Storage(final TestContext aContext) {
        LOGGER.debug(MessageCodes.AVPT_003, myTestName.getMethodName());

        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();
        final CsvItem csvItem = new CsvItem();

        // Create the CSV item we want the waveform verticle to process
        csvItem.setItemARK("ark:/21198/zz002dvxmm");
        csvItem.setFilePath("soul/audio/uclapasc.wav");

        vertx.eventBus().<JsonObject>request(WaveformVerticle.class.getName(), csvItem).compose(transformation -> {
            final String audiowaveformURL = transformation.body().getString(csvItem.getItemARK());

            // Retrieve the data from S3 via HTTP
            return WebClient.create(vertx).getAbs(audiowaveformURL).send();
        }).onSuccess(response -> {
            // Compare the data retrieved from S3 with a local test fixture
            final Buffer expected =
                    vertx.fileSystem().readFileBlocking("src/test/resources/soul/audio/uclapasc.dat.gz");
            final Buffer actual = response.body();

            // Partition the GZIP data into the header, body, and footer (according to RFC 1952)
            final Buffer expectedHeader = expected.getBuffer(0, 10);
            final Buffer actualHeader = actual.getBuffer(0, 10);

            final Buffer expectedBody = expected.getBuffer(10, expected.length() - 8);
            final Buffer actualBody = actual.getBuffer(10, actual.length() - 8);

            final Buffer expectedFooter = expected.getBuffer(expected.length() - 8, expected.length());
            final Buffer actualFooter = actual.getBuffer(actual.length() - 8, actual.length());

            try {
                // Apparently JDK 11 doesn't implement RFC 1952 correctly (i.e., it always sets the OS field (the
                // last byte in the header) to "0"), so only compare the first nine bytes
                assertEquals(expectedHeader.getBuffer(0, expectedHeader.length() - 1),
                        actualHeader.getBuffer(0, actualHeader.length() - 1));
                assertEquals(expectedBody, actualBody);
                assertEquals(expectedFooter, actualFooter);

                asyncTask.complete();
            } catch (final AssertionError details) {
                aContext.fail(details);
            }
        }).onFailure(error -> aContext.fail(error));
    }
}
