
package edu.ucla.library.avpairtree;

/**
 * A/V Pairtree API operation IDs.
 */
public final class Op {

    /**
     * Gets the application's status.
     */
    public static final String GET_STATUS = "getStatus";

    /**
     * The indication of a successful operation.
     */
    public static final String SUCCESS = "success";

    /**
     * A generic error code that we use for all message errors.
     */
    public static final int ERROR_CODE = 500;

    /**
     * Constant class constructors should be private.
     */
    private Op() {
    }

}
