
package info.freelibrary.vertx.template.verticles;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.HTTP;

import info.freelibrary.vertx.template.Config;
import info.freelibrary.vertx.template.utils.TestConstants;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests the main verticle of the Vert.x application.
 */
@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

    /**
     * Rule that creates the test context.
     */
    @Rule
    public RunTestOnContext myContext = new RunTestOnContext();

    /**
     * Sets up the test.
     *
     * @param aContext A test context
     */
    @Before
    public void setUp(final TestContext aContext) {
        final int port = Integer.parseInt(System.getProperty(Config.HTTP_PORT));
        final DeploymentOptions options = new DeploymentOptions();
        final Async asyncTask = aContext.async();

        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));
        myContext.vertx().deployVerticle(MainVerticle.class.getName(), options)
                .onSuccess(result -> asyncTask.complete()).onFailure(error -> aContext.fail(error));
    }

    /**
     * Tests the server can start successfully.
     *
     * @param aContext A test context
     */
    @Test
    public void testThatTheServerIsStarted(final TestContext aContext) {
        final WebClient client = WebClient.create(myContext.vertx());
        final Async asyncTask = aContext.async();

        client.get(aContext.get(Config.HTTP_PORT), TestConstants.INADDR_ANY, "/status").send(get -> {
            if (get.succeeded()) {
                aContext.assertEquals(HTTP.OK, get.result().statusCode());
                complete(asyncTask);
            } else {
                aContext.fail(get.cause());
            }
        });
    }

    /**
     * A convenience method to end asynchronous tasks.
     *
     * @param aAsyncTask A task to complete
     */
    private void complete(final Async aAsyncTask) {
        if (!aAsyncTask.isCompleted()) {
            aAsyncTask.complete();
        }
    }
}
