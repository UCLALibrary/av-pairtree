
package edu.ucla.library.avpairtree;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * An abstract test that other tests can extend.
 */
@RunWith(VertxUnitRunner.class)
public class JobsQueueTest {

    /**
     * JobsQueueTest logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsQueueTest.class, MessageCodes.BUNDLE);

    private static final String SOUL_WAV = "soul/audio/uclapasc.wav";

    private static final String SYNANON_MP4 = "synanon/video/synanon.mp4";

    private static final String ARK_ADD = "ark:/21198/zz002dvxmm";

    private static final String ARK_REM = "ark:/21198/zz002dvxmn";

    private static final String PATH_ROOT = "soul";

    private static final String MAP = "jobs-queue";

    /**
     * Rule that creates the test context.
     */
    @Rule
    public RunTestOnContext myContext = new RunTestOnContext();

    /**
     * Rule that provides access to the test method name.
     */
    @Rule
    public TestName myNames = new TestName();

    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Test adding job to shared map
     * @param aContext A test context
     */
    @Test
    public void TestAddJobToQueue(final TestContext aContext) {
        final CsvItem aItem = new CsvItem();
        final Vertx vertx = myContext.vertx();
        final Async asyncTask = aContext.async();

        aItem.setItemARK(ARK_ADD);
        aItem.setFilePath(SOUL_WAV);
        aItem.setPathRoot(PATH_ROOT);

        final JobsQueue JobsQueue = new JobsQueue(vertx);
        final Future<Void> future = JobsQueue.addJobToQueue(aItem);

        future.onSuccess(result -> {
            vertx.sharedData().<String, CsvItem>getLocalAsyncMap(MAP, getMap -> {
                if (getMap.succeeded()) {
                    final AsyncMap<String, CsvItem> jobsQueue = getMap.result();
                    jobsQueue.get(ARK_ADD).onSuccess(res -> {
                        assertEquals(aItem, res);
                        asyncTask.complete();
                    });
                } else {
                    aContext.fail(getMap.cause());
                }
            });
        }).onFailure(failure -> {
            aContext.fail(failure);
        });
    }

    /**
     * Test removing jobs from shared map
     * @param aContext A test context
     */
    @Test
    public void TestRemoveJobInQueue(final TestContext aContext) {
        final CsvItem aItem = new CsvItem();
        final Async asyncTask = aContext.async();
        final Vertx vertx = myContext.vertx();


        aItem.setItemARK(ARK_REM);
        aItem.setFilePath(SOUL_WAV);
        aItem.setPathRoot(PATH_ROOT);

        final JobsQueue JobsQueue = new JobsQueue(vertx);

        //Put item in shared map
        vertx.sharedData().<String, CsvItem>getLocalAsyncMap(MAP, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, CsvItem> jobsMap = getMap.result();

                jobsMap.put(ARK_REM, aItem, result -> {
                    if (result.failed()) {
                        aContext.fail(result.cause());
                    }
                });

                final Future<Void> future = JobsQueue.removeJobInQueue(ARK_REM);
                jobsMap.get(ARK_REM).onFailure(failure -> {
                    aContext.fail();
                }).onSuccess(res -> {
                    assertEquals(res, null);
                    asyncTask.complete();
                });
            } else {
                aContext.fail(getMap.cause());
            }
        });
    }
}
