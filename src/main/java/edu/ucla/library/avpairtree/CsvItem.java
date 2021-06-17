
package edu.ucla.library.avpairtree;

import org.csveed.annotations.CsvCell;
import org.csveed.annotations.CsvFile;
import org.csveed.annotations.CsvIgnore;
import org.csveed.bean.ColumnNameMapper;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import info.freelibrary.util.Constants;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * The required metadata from the supplied CSV file.
 */
@CsvFile(quote = '"', separator = ',', escape = '"', mappingStrategy = ColumnNameMapper.class)
public class CsvItem {

    /**
     * The CSV header column for the IIIF access URL. Note that this not used for deserialization; see
     * WatcherVerticle.updateCSV for its use in serialization.
     */
    @CsvIgnore
    public static final String IIIF_ACCESS_URL_HEADER = "IIIF Access URL";

    /**
     * The CSV header column for the Waveform. Note that this not used for deserialization; see
     * WatcherVerticle.updateCSV for its use in serialization.
     */
    @CsvIgnore
    public static final String WAVEFORM_HEADER = "Waveform";

    /**
     * The CSV header column for the item's identifier.
     */
    @CsvIgnore
    public static final String ITEM_ARK_HEADER = "Item ARK";

    /**
     * The CSV header column for the item's file name.
     */
    @CsvIgnore
    public static final String FILE_NAME_HEADER = "File Name";

    /**
     * The item ARK property used in JSON serialization.
     */
    @CsvIgnore
    private static final String ITEM_ARK = "ItemARK";

    /**
     * The file path property used in JSON serialization.
     */
    @CsvIgnore
    private static final String FILE_PATH = "FilePath";

    /**
     * The file root property used in JSON serialization.
     */
    @CsvIgnore
    private static final String PATH_ROOT = "PathRoot";

    /**
     * The processing output result property.
     */
    @CsvIgnore
    private static final String PROCESSING_RESULT = "Processed";

    /**
     * The property mapped to the item ARK column in the CSV file.
     */
    @CsvCell(columnName = ITEM_ARK_HEADER, required = true)
    private String myItemARK;

    /**
     * The property mapped to the file name column in the CSV file.
     */
    @CsvCell(columnName = FILE_NAME_HEADER)
    private String myFilePath;

    /**
     * The root of the relative file system path.
     */
    @CsvIgnore
    private String myPathRoot;

    /**
     * Whether the item processing was successful or not.
     */
    @CsvIgnore
    private boolean myProcessingStatus;

    /**
     * Gets the item's ARK.
     *
     * @return The item's ARK
     */
    @JsonGetter(ITEM_ARK)
    public String getItemARK() {
        return myItemARK;
    }

    /**
     * Sets the item's ARK.
     *
     * @param aItemARK The item's ARK
     */
    @JsonSetter(ITEM_ARK)
    public void setItemARK(final String aItemARK) { // return must be void because this is a bean
        myItemARK = aItemARK;
    }

    /**
     * Gets the item's file name.
     *
     * @return The item's file name
     */
    @JsonGetter(FILE_PATH)
    public String getFilePath() {
        return myFilePath;
    }

    /**
     * Sets the item's file path.
     *
     * @param aFilePath A file name
     */
    @JsonSetter(FILE_PATH)
    public void setFilePath(final String aFilePath) { // return must be void because this is a bean
        myFilePath = aFilePath;
    }

    /**
     * Gets the item's path root.
     *
     * @return The path root
     */
    @JsonSetter(PATH_ROOT)
    public String getPathRoot() {
        return myPathRoot;
    }

    /**
     * Sets the path root of the item.
     *
     * @param aFilePath A file path
     * @return The CSV item
     */
    @JsonSetter(PATH_ROOT)
    public CsvItem setPathRoot(final String aFilePath) {
        if (aFilePath != null) {
            myPathRoot = aFilePath.split(Constants.SLASH)[0];
        } else {
            myPathRoot = null;
        }

        return this;
    }

    /**
     * Returns whether the CSV data has an audio file name.
     *
     * @return True if the CSV data has an audio file name; else, false
     */
    @JsonIgnore
    public boolean isAudio() {
        // Our archival source audio files use wav as their format
        return myFilePath != null && myFilePath.endsWith(".wav");
    }

    /**
     * Returns whether the CSV data has a video file name.
     *
     * @return True if the CSV data has a video file name; else, false
     */
    @JsonIgnore
    public boolean isVideo() {
        // Our archival source video files use mp4 as their format
        return myFilePath != null && myFilePath.endsWith(".mp4");
    }

    /**
     * Sets whether the item has been processed: <code>true</code> if it has and <code>false</code> if it hasn't.
     *
     * @param aBool A boolean flag indicating processing status
     * @return The CSV item
     */
    @JsonSetter(PROCESSING_RESULT)
    public CsvItem setProcessingStatus(final boolean aBool) {
        myProcessingStatus = aBool;
        return this;
    }

    /**
     * Gets whether the item has been successfully processed.
     *
     * @return True if the item has been processed; else, false
     */
    @JsonGetter(PROCESSING_RESULT)
    public boolean isProcessed() {
        return myProcessingStatus;
    }

    @Override
    public String toString() {
        return toJSON().encodePrettily();
    }

    /**
     * Returns a JSON representation of the CSV data.
     *
     * @return A JSON representation of the CSV data
     */
    JsonObject toJSON() {
        return JsonObject.mapFrom(this);
    }

    /**
     * Returns CSV data from its JSON representation.
     *
     * @param aJsonObject The CSV data in JSON form
     * @return The CSV data
     */
    static CsvItem fromJSON(final JsonObject aJsonObject) {
        return Json.decodeValue(aJsonObject.toString(), CsvItem.class);
    }

    /**
     * Returns a CSV data from its JSON representation.
     *
     * @param aJsonString The CSV data in string form
     * @return The CSV data
     */
    static CsvItem fromString(final String aJsonString) {
        return fromJSON(new JsonObject(aJsonString));
    }
}
