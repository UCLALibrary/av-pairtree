
package edu.ucla.library.avpairtree.handlers;

import info.freelibrary.util.HTTP;

import edu.ucla.library.avpairtree.AvPtConstants;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that returns the application's status.
 */
public class StatusHandler implements Handler<RoutingContext> {

    /**
     * The handler's copy of the Vert.x instance.
     */
    private final Vertx myVertx;

    /**
     * Creates a new handler to respond to status requests.
     *
     * @param aVertx A Vert.x instance
     */
    public StatusHandler(final Vertx aVertx) {
        myVertx = aVertx;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final JsonObject status = new JsonObject();

        status.put(AvPtConstants.STATUS, "ok");

        response.setStatusCode(HTTP.OK);
        response.putHeader(HttpHeaders.CONTENT_TYPE, AvPtConstants.JSON).end(status.encodePrettily());
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
