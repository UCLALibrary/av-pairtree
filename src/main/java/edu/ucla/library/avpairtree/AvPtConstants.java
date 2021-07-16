
package edu.ucla.library.avpairtree;

/**
 * A class of constants .
 */
public final class AvPtConstants {

    /**
     * A property for reporting application status.
     */
    public static final String STATUS = "status";

    /**
     * A media-type for the response from the application's status endpoint.
     */
    public static final String JSON = "application/json";

    /**
     * The system's temporary files directory.
     */
    public static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * The event bus address of the consumer of waveform data (typically an Amazon S3 client, but sometimes a Localstack
     * S3 client for integration testing).
     */
    public static final String WAVEFORM_CONSUMER = "waveform-consumer";

    /*
     * Constant classes have private constructors.
     */
    private AvPtConstants() {
    }

}
