
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
     * The template string for AWS S3 resource URLs. The slots are (in order): bucket name, region, and object key.
     */
    public static final String AUDIOWAVEFORM_URL_TEMPLATE = "https://{}.s3-{}.amazonaws.com/{}";

    /*
     * Constant classes have private constructors.
     */
    private AvPtConstants() {
    }

}
