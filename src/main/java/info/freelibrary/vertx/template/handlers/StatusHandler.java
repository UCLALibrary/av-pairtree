
package info.freelibrary.vertx.template.handlers;

import info.freelibrary.util.HTTP;

import info.freelibrary.vertx.template.TemplateConstants;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that accepts requests to mint ARKs.
 */
public class StatusHandler implements Handler<RoutingContext> {

    /**
     * The handler's copy of the Vert.x instance.
     */
    private final Vertx myVertx;

    /**
     * Creates a handler that mints ARKs.
     *
     * @param aVertx
     */
    public StatusHandler(final Vertx aVertx) {
        myVertx = aVertx;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final JsonObject status = new JsonObject();

        status.put(TemplateConstants.STATUS, "ok");

        response.setStatusCode(HTTP.OK);
        response.putHeader(HttpHeaders.CONTENT_TYPE, TemplateConstants.JSON).end(status.encodePrettily());
    }

    /**
     * Gets the Vert.x instance associated with this handler.
     *
     * @return The Vert.x instance associated with this handler
     */
    public Vertx getVertx() {
        return myVertx;
    }
}
