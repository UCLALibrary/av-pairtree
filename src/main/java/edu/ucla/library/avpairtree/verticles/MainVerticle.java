
package edu.ucla.library.avpairtree.verticles;

import static edu.ucla.library.avpairtree.AvPtConstants.WAVEFORM_CONSUMER;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.CsvItemCodec;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;
import edu.ucla.library.avpairtree.handlers.StatusHandler;
import edu.ucla.library.avpairtree.handlers.AmazonS3WaveformConsumer;

import io.methvin.watcher.DirectoryWatcher;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.openapi.RouterBuilder;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    /**
     * The map of verticle names and deployment IDs.
     */
    public static final String VERTICLES_MAP = "verticles.map";

    /**
     * The logger used for logging messages from the MainVerticle.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MessageCodes.BUNDLE);

    /**
     * The location of the OpenAPI specification for this application.
     */
    private static final String API_SPEC = "src/main/resources/av-pairtree-openapi.yaml";

    /**
     * A default number of worker threads to use.
     */
    private static final int DEFAULT_WORKER_COUNT = 2;

    /**
     * Indication if a verticle is a worker or not.
     */
    private static final String WORKER = "worker";

    /**
     * The drop box watcher.
     */
    private DirectoryWatcher myWatcher;

    /**
     * The internal HTTP server.
     */
    private HttpServer myServer;

    @Override
    public void start(final Promise<Void> aPromise) {
        ConfigRetriever.create(vertx).getConfig()
            .onSuccess(config -> configureServer(config.mergeIn(config()), aPromise))
            .onFailure(error -> aPromise.fail(error));
    }

    @Override
    public void stop(final Promise<Void> aPromise) {
        final Promise<Void> promise = Promise.promise();

        // Close the watcher, then close the server... chain the futures into one result
        promise.future().compose(watcherClose -> myServer.close().onComplete(cleanUp -> {
            if (cleanUp.succeeded()) {
                aPromise.complete();
            } else {
                aPromise.fail(cleanUp.cause());
            }
        }));

        try {
            myWatcher.close();
            promise.complete();
        } catch (final IOException details) {
            promise.fail(details);
        }
    }

    /**
     * Configure the application server.
     *
     * @param aConfig A JSON configuration
     * @param aPromise A startup promise
     */
    private void configureServer(final JsonObject aConfig, final Promise<Void> aPromise) {
        final int port = aConfig.getInteger(Config.HTTP_PORT, 8888);
        final String host = aConfig.getString(Config.HTTP_HOST, "0.0.0.0");

        // Build the application's HTTP router from the project's OpenAPI specification
        RouterBuilder.create(vertx, API_SPEC).onComplete(routerConfig -> {
            if (routerConfig.succeeded()) {
                final HttpServerOptions serverOptions = new HttpServerOptions().setPort(port).setHost(host);
                final RouterBuilder routerBuilder = routerConfig.result();
                final Vertx vertx = getVertx();

                // Associate handlers with operation IDs from the application's OpenAPI specification
                routerBuilder.operation(Op.GET_STATUS).handler(new StatusHandler(getVertx()));

                // Create the application server
                myServer = vertx.createHttpServer(serverOptions).requestHandler(routerBuilder.createRouter());

                // Start the application server
                myServer.listen().onSuccess(serverStartup -> {
                    @SuppressWarnings("rawtypes")
                    final List<Future> futures = new ArrayList<>();

                    futures.add(deployVerticle(new WatcherVerticle(), aConfig));
                    futures.add(deployVerticle(new PairtreeVerticle(), aConfig));
                    futures.add(deployVerticle(new ConverterVerticle(), aConfig.copy().put(WORKER, true)));
                    futures.add(deployVerticle(new WaveformVerticle(), aConfig.copy().put(WORKER, true)));

                    CompositeFuture.all(futures).onSuccess(result -> {
                        try {
                            // Configure the waveform consumer
                            vertx.eventBus().<byte[]>consumer(WAVEFORM_CONSUMER,
                                    new AmazonS3WaveformConsumer(aConfig)::handle);

                            startCsvDirWatcher(aConfig).onComplete(startup -> {
                                // Register the codec for passing CsvItem(s) over the event bus
                                vertx.eventBus().registerDefaultCodec(CsvItem.class, new CsvItemCodec());

                                if (startup.succeeded()) {
                                    LOGGER.info(MessageCodes.AVPT_001, port); // Log a successful startup w/ port
                                    aPromise.complete();
                                } else {
                                    aPromise.fail(startup.cause());
                                }
                            });
                        } catch (final IllegalStateException details) {
                            aPromise.fail(details.getCause());
                        }
                    }).onFailure(error -> aPromise.fail(error));
                }).onFailure(error -> aPromise.fail(error));
            } else {
                aPromise.fail(routerConfig.cause());
            }
        });
    }

    /**
     * Deploys a supplied verticle.
     *
     * @param aVerticle A verticle to deploy
     * @return A future where the verticle has, hopefully, been deployed
     */
    private Future<Void> deployVerticle(final Verticle aVerticle, final JsonObject aConfig) {
        final DeploymentOptions options = new DeploymentOptions().setConfig(aConfig);
        final Promise<Void> promise = Promise.promise();
        final Class<?> verticleClass = aVerticle.getClass();

        // If the configuration for this verticle mentions it should be a worker, find out how many to set
        if (aConfig.getBoolean(WORKER, false)) {
            final int nWorkerInstances;

            if (ConverterVerticle.class.equals(verticleClass)) {
                nWorkerInstances = aConfig.getInteger(Config.CONVERSION_WORKERS, DEFAULT_WORKER_COUNT);
            } else if (WaveformVerticle.class.equals(verticleClass)) {
                nWorkerInstances = aConfig.getInteger(Config.WAVEFORM_WORKERS, DEFAULT_WORKER_COUNT);
            } else {
                nWorkerInstances = DEFAULT_WORKER_COUNT;
            }
            options.setWorker(true).setWorkerPoolName(verticleClass.getSimpleName());
            options.setInstances(nWorkerInstances);
            options.setMaxWorkerExecuteTime(Integer.MAX_VALUE).setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES);

            LOGGER.debug(MessageCodes.AVPT_012, options.getInstances(), options.getWorkerPoolName());
        }

        vertx.deployVerticle(verticleClass.getName(), options).onSuccess(deploymentID -> {
            mapDeploymentID(verticleClass.getName(), deploymentID).onComplete(mapping -> {
                if (mapping.succeeded()) {
                    promise.complete();
                } else {
                    promise.fail(mapping.cause());
                }
            });
        }).onFailure(error -> promise.fail(error));

        return promise.future();
    }

    /**
     * Map the supplied ID and verticle name, and deploy the next verticle (if there is one).
     *
     * @param aVerticleName A verticle name
     * @param aDeploymentID A deployment ID for the supplied verticle name
     * @param aNextVerticle The next verticle to deploy
     * @return A future with the next verticle's deployment ID
     */
    private Future<Void> mapDeploymentID(final String aVerticleName, final String aDeploymentID) {
        final Promise<Void> promise = Promise.promise();

        getVertx().sharedData().getLocalAsyncMap(VERTICLES_MAP).onSuccess(map -> {
            map.put(aVerticleName, aDeploymentID).onComplete(mapping -> {
                if (mapping.succeeded()) {
                    promise.complete();
                } else {
                    promise.fail(mapping.cause());
                }
            });
        }).onFailure(error -> promise.fail(error));

        return promise.future();
    }

    /**
     * Starts a watcher that monitors our CSV drop box directory.
     *
     * @return Whether the directory watcher was started successfully
     */
    private Future<Void> startCsvDirWatcher(final JsonObject aConfig) {
        final String dirPath = aConfig.getString(Config.CSV_DIR);
        final Promise<Void> promise = Promise.promise();

        try {
            myWatcher = DirectoryWatcher.builder().path(Path.of(dirPath)).listener(event -> {
                final String filePath = event.path().toAbsolutePath().toString();

                // Watch CSV files that are created and modified
                switch (event.eventType()) {
                    case CREATE:
                    case MODIFY:
                        if (!event.isDirectory() && filePath.endsWith(".csv")) {
                            final DeliveryOptions options = new DeliveryOptions().setSendTimeout(Integer.MAX_VALUE);
                            vertx.eventBus().request(WatcherVerticle.class.getName(), filePath, options);
                        }
                    default:
                        // Ignore everything else (e.g., deletions, directory creation, etc.)
                }
            }).build();

            // Start the directory watcher in a background thread
            myWatcher.watchAsync();
            promise.complete();
        } catch (final IOException details) {
            promise.fail(details);
        }

        return promise.future();
    }
}
