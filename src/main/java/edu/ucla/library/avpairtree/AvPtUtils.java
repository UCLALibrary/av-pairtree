
package edu.ucla.library.avpairtree;

import java.nio.file.Path;

import info.freelibrary.util.Constants;

/**
 * A class for utility methods.
 */
public final class AvPtUtils {

    /**
     * Creates a new AVPT utilities.
     */
    private AvPtUtils() {
        // This intentionally left empty.
    }

    /**
     * Gets the input file path from available variables.
     *
     * @param aCsvItem A CSV item sent to us over the wire
     * @param aSourceDir A pre-configured source files directory
     * @return A file system path for the input file
     */
    public static Path getInputFilePath(final CsvItem aCsvItem, final String aSourceDir) {
        final String filePath = aCsvItem.getFilePath();

        // Use our source folder unless we receive a file path that is absolute
        if (!filePath.startsWith(Constants.SLASH)) {
            return Path.of(aSourceDir, filePath);
        }
        return Path.of(filePath);
    }
}
