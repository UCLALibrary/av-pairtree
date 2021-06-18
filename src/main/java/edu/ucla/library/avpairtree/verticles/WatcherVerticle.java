
package edu.ucla.library.avpairtree.verticles;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.csveed.api.CsvClient;
import org.csveed.api.CsvClientImpl;
import org.csveed.api.Header;
import org.csveed.report.CsvException;

import info.freelibrary.util.Constants;
import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import info.freelibrary.pairtree.Pairtree;
import info.freelibrary.pairtree.PairtreeUtils;

import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * A verticle that responds to events from the CSV directory watcher.
 */
public class WatcherVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatcherVerticle.class, MessageCodes.BUNDLE);

    private static final String SUBSTITUTION_PATTERN = "{}";

    @Override
    public void start(final Promise<Void> aPromise) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(Integer.MAX_VALUE);
        final Vertx vertx = getVertx();
        final EventBus eventBus = vertx.eventBus();

        // Consume messages containing a path location to an uploaded CSV file
        eventBus.<String>consumer(getClass().getName()).handler(message -> {
            LOGGER.info(MessageCodes.AVPT_008, message.body());

            // Read the CSV file and send audio and video items for further processing
            vertx.fileSystem().readFile(message.body()).onSuccess(csvBuffer -> {
                final StringReader csvReader = new StringReader(csvBuffer.toString(StandardCharsets.UTF_8));
                final CsvClient<CsvItem> reader = new CsvClientImpl<>(csvReader, CsvItem.class);
                @SuppressWarnings("rawtypes") // Composite futures don't support typing
                final List<Future> futures = new ArrayList<>();

                reader.readBeans().forEach(item -> {
                    item.setPathRoot(item.getFilePath());

                    if (item.isAudio()) {
                        // Audio gets converted from wav to a more Web-friendly format, and a waveform file is generated
                        futures.add(eventBus.<CsvItem>request(ConverterVerticle.class.getName(), item, options));
                        futures.add(eventBus.<JsonObject>request(WaveformVerticle.class.getName(), item, options));
                    } else if (item.isVideo()) { // Videos are already in mp4 format so don't need conversion
                        futures.add(eventBus.<CsvItem>request(PairtreeVerticle.class.getName(), item, options));
                    } // else, ignore
                });

                CompositeFuture.all(futures).onSuccess(conversions -> {
                    final List<Message<?>> results = conversions.result().list();

                    // Filter the audiowaveform URLs out of the results and combine them all into a single JsonObject,
                    // which we'll use as a lookup table when updating the CSV with audiowaveform URLs
                    final JsonObject waveformUriMap = results.stream()
                            .filter(result -> result.body().getClass().equals(JsonObject.class))
                            .map(msg -> (JsonObject) msg.body())
                            .reduce(new JsonObject(), (jsonObject1, jsonObject2) -> jsonObject1.mergeIn(jsonObject2));

                    // Map ARKs to their corresponding CsvItem
                    final Map<String, CsvItem> csvItemMap = results.stream()
                            .filter(result -> result.body().getClass().equals(CsvItem.class))
                            .map(msg -> {
                                final CsvItem item = (CsvItem) msg.body();

                                LOGGER.info(MessageCodes.AVPT_009, item.getItemARK());
                                return item;
                            })
                            .collect(Collectors.toMap(item -> item.getItemARK(), item -> item));

                    updateCSV(message.body(), csvItemMap, waveformUriMap).onSuccess(csvFilePath -> {
                        LOGGER.info(MessageCodes.AVPT_006, csvFilePath);
                        message.reply(Op.SUCCESS);
                    }).onFailure(error -> {
                        LOGGER.error(error, error.getMessage());
                        message.fail(Op.ERROR_CODE, error.getMessage());
                    });
                }).onFailure(error -> LOGGER.error(error, error.getMessage()));
            }).onFailure(error -> LOGGER.error(error, error.getMessage()));
        });

        aPromise.complete();
    }

    /**
     * Update the CSV file with our newly created IIIF access URLs and waveform URLs.
     *
     * @param aCsvFilePath The path to the existing CSV file
     * @param aCsvItemMap A map of ARKs to the items that have been processed
     * @param aWaveformMap A map of ARKs to audiowaveform URLs for the items that have been processed
     * @return The path of the new CSV file
     */
    private Future<String> updateCSV(final String aCsvFilePath, final Map<String, CsvItem> aCsvItemMap,
            final JsonObject aWaveformMap) {
        final String newCsvPath = FileUtils.stripExt(aCsvFilePath) + ".out"; // Would be re-watched if ext was .csv
        final Promise<String> promise = Promise.promise();

        // Read CSV file in a non-blocking manner and then do something with the data
        vertx.fileSystem().readFile(aCsvFilePath).onSuccess(csvBuffer -> {
            final StringReader csvReader = new StringReader(csvBuffer.toString(StandardCharsets.UTF_8));
            final CsvClient<CsvItem> reader = new CsvClientImpl<>(csvReader, CsvItem.class);

            // Open the CSV file we'll be writing the updated information to
            try (FileWriter csvWriter = new FileWriter(new File(newCsvPath))) {
                final CsvClient<?> writer = new CsvClientImpl<>(csvWriter);
                final Header originalHeader = reader.readHeader();

                final int originalRowSize = originalHeader.size();
                final int originalAccessUrlIndex = getUrlAccessIndex(originalHeader);
                final int originalWaveformIndex = getWaveformIndex(originalHeader);

                final int rowSize;
                final int accessUrlIndex;
                final int waveformIndex;

                // Override the unusual out of the box defaults for the writer
                writer.setEscape('"').setQuote('"').setSeparator(',');

                // Deal with the header row first; unless both columns exist already, we have to modify the underlying
                // array (it's arguably a limitation of CSVeed that we have to do that)
                if (originalWaveformIndex != -1 && originalAccessUrlIndex != -1) {
                    // Both columns exist already, so just use the header as-is; record some useful metadata first
                    rowSize = originalRowSize;
                    accessUrlIndex = originalAccessUrlIndex;
                    waveformIndex = originalWaveformIndex;

                    writer.writeHeader(originalHeader);
                } else {
                    final String[] headerRow;

                    if (originalAccessUrlIndex != -1) {
                        // IIIF Access URL exists, but not Waveform; expand CSV by one column
                        rowSize = originalRowSize + 1;
                        accessUrlIndex = originalAccessUrlIndex;
                        waveformIndex = rowSize - 1;
                    } else {
                        // Neither IIIF Access URL or Waveform exist in the CSV yet (Waveform cannot possibly exist
                        // without IIIF Access URL); expand CSV by two columns
                        rowSize = originalRowSize + 2;
                        accessUrlIndex = rowSize - 2;
                        waveformIndex = rowSize - 1;
                    }
                    headerRow = new String[rowSize];

                    for (int index = 0; index < rowSize; index++) {
                        // Put the new header fields where they belong
                        if (accessUrlIndex == index) {
                            headerRow[index] = CsvItem.IIIF_ACCESS_URL_HEADER;
                        } else if (waveformIndex == index) {
                            headerRow[index] = CsvItem.WAVEFORM_HEADER;
                        } else {
                            // Copy values from the original header row to the new one
                            headerRow[index] = originalHeader.getName(index + 1); // Row and Header indices are 1-based
                        }
                    }

                    writer.writeHeader(headerRow);
                }

                // Now, stream through all the non-header rows
                reader.readRows().stream().forEach(originalRow -> {
                    final String ark = originalRow.get(CsvItem.ITEM_ARK_HEADER);
                    final CsvItem csvItem = aCsvItemMap.get(ark);
                    final String[] row = new String[rowSize];

                    for (int index = 0; index < rowSize; index++) {
                        if (accessUrlIndex == index) {
                            if (aCsvItemMap.containsKey(ark)) {
                                row[index] = constructAccessURL(csvItem);
                            } else {
                                if (originalAccessUrlIndex != -1) {
                                    // Don't overwrite what was already there (e.g. in the case of images)
                                    row[index] = originalRow.get(index + 1);
                                } else {
                                    row[index] = "";
                                }
                            }
                        } else if (waveformIndex == index) {
                            row[index] = aWaveformMap.getString(ark, "");
                        } else {
                            row[index] = originalRow.get(index + 1);
                        }
                    }

                    writer.writeRow(row);
                });

                csvWriter.close();
                promise.complete(newCsvPath);
            } catch (final Exception details) {
                promise.fail(details);
            }
        });

        return promise.future();
    }

    /**
     * Gets the IIIF access URL index.
     *
     * @param aHeader A header
     * @return The IIIF access URL index
     */
    private int getUrlAccessIndex(final Header aHeader) {
        try {
            return aHeader.getIndex(CsvItem.IIIF_ACCESS_URL_HEADER) - 1; // Row and Header indices are 1-based
        } catch (final CsvException details) {
            return -1; // Seems odd to throw an exception for this
        }
    }

    /**
     * Gets the waveform index.
     *
     * @param aHeader A header
     * @return The waveform index
     */
    private int getWaveformIndex(final Header aHeader) {
        try {
            return aHeader.getIndex(CsvItem.WAVEFORM_HEADER) - 1;
        } catch (final CsvException details) {
            return -1;
        }
    }

    /**
     * Encodes the Pairtree path in the A/V server's access URL.
     *
     * @param aCsvItem An item from the CSV file
     * @return An encoded path for the A/V server's access URL
     * @throws IndexOutOfBoundsException If a supplied ID index position isn't valid
     * @throws UnsupportedOperationException If the access URL pattern doesn't contain any placeholders
     */
    private String constructAccessURL(final CsvItem aCsvItem)
            throws IndexOutOfBoundsException, UnsupportedOperationException {
        final String arkPrefix = config().getString(Config.PAIRTREE_PREFIX);
        final String ark = aCsvItem.getItemARK();
        final String pathARK = ark.replace(arkPrefix, Constants.EMPTY); // Strip ARK prefix
        final String slug = aCsvItem.getPathRoot();
        final String encodedARK = PairtreeUtils.encodeID(ark);
        final String fileExt = Constants.PERIOD + config().getString(Config.ENCODING_FORMAT);
        final String accessUrlPattern = config().getString(Config.ACCESS_URL_PATTERN, SUBSTITUTION_PATTERN);
        final int urlPatternIdIndex = config().getInteger(Config.ACCESS_URL_ID_INDEX, 1);
        final String ptPath = PairtreeUtils.mapToPtPath(slug + Constants.SLASH + Pairtree.ROOT, pathARK, pathARK);
        final String ptFilePath = (ptPath + Constants.SLASH + encodedARK + fileExt).replace(Constants.PLUS, "%2B");
        final String accessURL = addIdPath(accessUrlPattern, urlPatternIdIndex, ptFilePath);

        LOGGER.debug(MessageCodes.AVPT_010, ark, accessURL);

        return accessURL;
    }

    /**
     * Gets the IIIF access URL.
     *
     * @param aAccessUrlPattern A textual pattern to use for the IIIF access URL
     * @param aUrlPatternIdIndex A position for the ID in the access URL pattern
     * @param aIdPath An ID's Pairtree path
     * @return The IIIF access URL
     * @throws IndexOutOfBoundsException If a supplied ID index position isn't valid
     * @throws UnsupportedOperationException If the access URL pattern doesn't contain any placeholders
     */
    private String addIdPath(final String aAccessUrlPattern, final int aUrlPatternIdIndex, final String aIdPath)
            throws IndexOutOfBoundsException, UnsupportedOperationException {
        final int substitutionCount = countSubstitutionPatterns(aAccessUrlPattern);

        // We allow up to three path substitutions, with the Pairtree path being able to be swapped into any of them
        switch (substitutionCount) {
            case 3:
                if (aUrlPatternIdIndex == 1) {
                    return StringUtils.format(aAccessUrlPattern, aIdPath, SUBSTITUTION_PATTERN, SUBSTITUTION_PATTERN);
                } else if (aUrlPatternIdIndex == 2) {
                    return StringUtils.format(aAccessUrlPattern, SUBSTITUTION_PATTERN, aIdPath, SUBSTITUTION_PATTERN);
                } else if (aUrlPatternIdIndex == 3) {
                    return StringUtils.format(aAccessUrlPattern, SUBSTITUTION_PATTERN, SUBSTITUTION_PATTERN, aIdPath);
                } else {
                    throw new IndexOutOfBoundsException(LOGGER.getMessage(MessageCodes.AVPT_013, aUrlPatternIdIndex));
                }
            case 2:
                if (aUrlPatternIdIndex == 1) {
                    return StringUtils.format(aAccessUrlPattern, aIdPath, SUBSTITUTION_PATTERN);
                } else if (aUrlPatternIdIndex == 2) {
                    return StringUtils.format(aAccessUrlPattern, SUBSTITUTION_PATTERN, aIdPath);
                } else {
                    throw new IndexOutOfBoundsException(LOGGER.getMessage(MessageCodes.AVPT_013, aUrlPatternIdIndex));
                }
            case 1:
                if (aUrlPatternIdIndex == 1) {
                    return StringUtils.format(aAccessUrlPattern, aIdPath);
                } else {
                    throw new IndexOutOfBoundsException(LOGGER.getMessage(MessageCodes.AVPT_013, aUrlPatternIdIndex));
                }
            default:
                throw new UnsupportedOperationException(LOGGER.getMessage(MessageCodes.AVPT_013, substitutionCount));
        }
    }

    /**
     * Counts the number of substitution patterns in the supplied string.
     *
     * @param aString A string with substitution patterns (e.g. <code>{}</code>)
     * @return The number of substitution patterns in the supplied string
     */
    private int countSubstitutionPatterns(final String aString) {
        final Pattern pattern = Pattern.compile(SUBSTITUTION_PATTERN, Pattern.LITERAL);
        final Matcher matcher = pattern.matcher(aString);

        int startIndex = 0;
        int count = 0;

        while (matcher.find(startIndex)) {
            startIndex = matcher.start() + 1;
            count += 1;
        }

        return count;
    }
}
