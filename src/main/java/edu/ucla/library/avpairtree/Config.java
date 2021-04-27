
package edu.ucla.library.avpairtree;

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

    /**
     * The configuration property for the CSV directory the program should watch.
     */
    public static final String CSV_DIR = "csv.dir";

    /**
     * The configuration property for the directory where source files can be found.
     */
    public static final String SOURCE_DIR = "source.dir";

    /**
     * The configuration property for the directory where media files should be written.
     */
    public static final String OUTPUT_DIR = "output.dir";

    /**
     * The configuration property for the output Pairtree's prefix.
     */
    public static final String PAIRTREE_PREFIX = "pairtree_prefix";

    /**
     * The configuration property for the output audio codec.
     */
    public static final String AUDIO_CODEC = "audio.codec";

    /**
     * The configuration property for the output audio file's bit rate.
     */
    public static final String BIT_RATE = "audio.bit.rate";

    /**
     * The configuration property for the output audio file's number of channels.
     */
    public static final String CHANNELS = "audio.channels";

    /**
     * The configuration property for the output audio file's sampling rate.
     */
    public static final String SAMPLING_RATE = "audio.sampling.rate";

    /**
     * The configuration property for the output audio file's encoding format.
     */
    public static final String ENCODING_FORMAT = "audio.encoding.format";

    /**
     * A configuration property for the pattern for creating IIIF access URLs.
     */
    public static final String ACCESS_URL_PATTERN = "iiif.access.url";

    /**
     * The configuration property for which substitution pattern in iiif.access.url should be the ID; this is 1-based,
     * not zero-based.
     */
    public static final String ACCESS_URL_ID_INDEX = "iiif.access.url.id.index";

    /**
     * The number of workers that should work to do media file conversions.
     */
    public static final String CONVERSION_WORKERS = "conversion.workers";

    // Constant classes should have private constructors.
    private Config() {
    }

}
