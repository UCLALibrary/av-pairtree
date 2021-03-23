
package info.freelibrary.vertx.template;

/**
 * Properties that are used to configure the application.
 */
public final class Config {

    /**
     * The configuration property for the application's port.
     */
    public static final String HTTP_PORT = "http.port";

    /**
     * The configuration property for the application's host.
     */
    public static final String HTTP_HOST = "http.host";

    // Constant classes should have private constructors
    private Config() {
    }

}
