
package edu.ucla.library.avpairtree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.ucla.library.avpairtree.utils.TestConstants;

import io.vertx.core.json.JsonObject;

/**
 * Tests of CsvItem.
 */
public class CsvItemTest {

    private static final String SOUL_WAV = "soul/audio/uclapasc.wav";

    private static final String SYNANON_MP4 = "synanon/video/synanon.mp4";

    private static final String ARK = "ark:/21198/zz002dvxmm";

    private static final String PATH_ROOT = "soul";

    /**
     * Tests the path root methods in CsvItem.
     */
    @Test
    public void testSetPathRoot() {
        assertEquals(PATH_ROOT, new CsvItem().setPathRoot(SOUL_WAV).getPathRoot());
    }

    /**
     * Tests the item ARK methods in CsvItem.
     */
    @Test
    public void testSetItemARK() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setItemARK(ARK);
        assertEquals(ARK, csvItem.getItemARK());
    }

    /**
     * Tests the file path methods in CsvItem.
     */
    @Test
    public void testSetFilePath() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setFilePath(SOUL_WAV);
        assertEquals(SOUL_WAV, csvItem.getFilePath());
    }

    /**
     * Tests the audio check.
     */
    @Test
    public void testIsAudioTrue() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setFilePath(SOUL_WAV);
        assertTrue(csvItem.isAudio());
    }

    /**
     * Tests the audio check with non-audio items.
     */
    @Test
    public void testIsAudioFalse() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setFilePath(SYNANON_MP4);
        assertFalse(csvItem.isAudio());
    }

    /**
     * Tests the video check.
     */
    @Test
    public void testIsVideoTrue() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setFilePath(SYNANON_MP4);
        assertTrue(csvItem.isVideo());
    }

    /**
     * Tests the video check with non-video items.
     */
    @Test
    public void testIsVideoFalse() {
        final CsvItem csvItem = new CsvItem();

        csvItem.setFilePath(SOUL_WAV);
        assertFalse(csvItem.isVideo());
    }

    /**
     * Tests checking and setting the processing status.
     */
    @Test
    public void testProcessingStatus() {
        assertTrue(new CsvItem().setProcessingStatus(true).isProcessed());
    }

    /**
     * Tests the default processing status value.
     */
    @Test
    public void testDefaultProcessingStatus() {
        assertFalse(new CsvItem().isProcessed());
    }

    /**
     * Tests CsvItem's toJSON method.
     */
    @Test
    public void testToJSON() {
        final CsvItem csvItem = new CsvItem();
        final JsonObject json = new JsonObject();

        csvItem.setItemARK(ARK);
        csvItem.setFilePath(SOUL_WAV);
        csvItem.setPathRoot(SOUL_WAV);

        json.put(TestConstants.PATH_ROOT, PATH_ROOT);
        json.put(TestConstants.ITEM_ARK, ARK);
        json.put(TestConstants.FILE_PATH, SOUL_WAV);
        json.put(TestConstants.PROCESSED, false);

        assertEquals(json, csvItem.toJSON());
    }

    /**
     * Tests CsvItem's toString method.
     */
    @Test
    public void testToString() {
        final CsvItem csvItem = new CsvItem();
        final JsonObject json = new JsonObject();

        csvItem.setItemARK(ARK);
        csvItem.setFilePath(SOUL_WAV);
        csvItem.setPathRoot(SOUL_WAV);

        json.put(TestConstants.PATH_ROOT, PATH_ROOT);
        json.put(TestConstants.ITEM_ARK, ARK);
        json.put(TestConstants.FILE_PATH, SOUL_WAV);
        json.put(TestConstants.PROCESSED, false);

        assertEquals(json, new JsonObject(csvItem.toString()));
    }
}
