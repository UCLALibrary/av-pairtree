
package info.freelibrary.vertx.template.verticles;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import info.freelibrary.vertx.template.Config;
import info.freelibrary.vertx.template.MessageCodes;
import info.freelibrary.vertx.template.Op;
import info.freelibrary.vertx.template.handlers.StatusHandler;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.openapi.RouterBuilder;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MessageCodes.BUNDLE);

    private static final String API_SPEC = "src/main/resources/template.yaml";

    private HttpServer myServer;

    @Override
    public void start(final Promise<Void> aPromise) {
        ConfigRetriever.create(vertx).getConfig()
                .onSuccess(config -> configureServer(config.mergeIn(config()), aPromise))
                .onFailure(error -> aPromise.fail(error));
    }

    @Override
    public void stop(final Promise<Void> aPromise) {
        myServer.close().onSuccess(result -> aPromise.complete()).onFailure(error -> aPromise.fail(error));
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

        RouterBuilder.create(vertx, API_SPEC).onComplete(routerConfig -> {
            if (routerConfig.succeeded()) {
                final HttpServerOptions serverOptions = new HttpServerOptions().setPort(port).setHost(host);
                final RouterBuilder routerBuilder = routerConfig.result();

                // Associate handlers with operation IDs from the application's OpenAPI specification
                routerBuilder.operation(Op.GET_STATUS).handler(new StatusHandler(getVertx()));

                myServer = getVertx().createHttpServer(serverOptions).requestHandler(routerBuilder.createRouter());
                myServer.listen().onSuccess(result -> {
                    LOGGER.info(MessageCodes.TMPL_001, port);
                    aPromise.complete();
                }).onFailure(error -> aPromise.fail(error));
            } else {
                aPromise.fail(routerConfig.cause());
            }
        });
    }
}
