
package edu.ucla.library.avpairtree.verticles;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

                    if (item.isAudio()) { // Audio gets converted from wav to a more Web-friendly format
                        futures.add(eventBus.request(ConverterVerticle.class.getName(), item, options));
                    } else if (item.isVideo()) { // Videos are already in mp4 format so don't need conversion
                        futures.add(eventBus.request(PairtreeVerticle.class.getName(), item, options));
                    } // else, ignore
                });

                CompositeFuture.all(futures).onSuccess(conversions -> {
                    final Map<String, CsvItem> arkMap = new HashMap<>();

                    conversions.result().list().stream().forEach(object -> {
                        @SuppressWarnings("unchecked") // Composite futures don't support typing
                        final CsvItem item = ((Message<CsvItem>) object).body();
                        final String ark = item.getItemARK();

                        LOGGER.info(MessageCodes.AVPT_009, ark);
                        arkMap.put(ark, item);
                    });

                    updateCSV(message.body(), arkMap).onSuccess(csvFilePath -> {
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
     * Update the CSV file with our newly created IIIF access URLs.
     *
     * @param aCsvFilePath The path to the existing CSV file
     * @param aArkMap A map of ARKs for the items that have been processed
     * @return The path of the new CSV file
     */
    private Future<String> updateCSV(final String aCsvFilePath, final Map<String, CsvItem> aArkMap) {
        final String newCsvPath = FileUtils.stripExt(aCsvFilePath) + ".out"; // Would be re-watched if ext was .csv
        final Promise<String> promise = Promise.promise();

        // Read CSV file in a non-blocking manner and then do something with the data
        vertx.fileSystem().readFile(aCsvFilePath).onSuccess(csvBuffer -> {
            final StringReader csvReader = new StringReader(csvBuffer.toString(StandardCharsets.UTF_8));
            final CsvClient<CsvItem> reader = new CsvClientImpl<>(csvReader, CsvItem.class);

            // Open the CSV file we'll be writing to with the updated information
            try (FileWriter csvWriter = new FileWriter(new File(newCsvPath))) {
                final CsvClient<?> writer = new CsvClientImpl<>(csvWriter);
                final Header header = reader.readHeader();
                final int accessUrlIndex = getUrlAccessIndex(header);

                // Override the unusual out of the box defaults for the writer
                writer.setEscape('"').setQuote('"').setSeparator(',');

                if (accessUrlIndex != -1) {
                    writer.writeHeader(header);
                } else {
                    final String[] headerRow = new String[header.size() + 1];

                    for (int index = 0; index < headerRow.length; index++) {
                        headerRow[index] = header.getName(index);
                    }

                    headerRow[header.size()] = CsvItem.IIIF_ACCESS_URL_HEADER;
                    writer.writeHeader(headerRow);
                }

                // Stream through all the rows in our CSV file
                reader.readRows().stream().forEach(row -> {
                    final String ark = row.get(CsvItem.ITEM_ARK_HEADER);

                    final int columnCount = row.size();
                    final String[] columns;

                    // Check to see if this CSV file has an existing "IIIF Access URL" column
                    if (accessUrlIndex == -1) {
                        columns = new String[columnCount + 1];

                        for (int index = 0; index < columnCount; index++) {
                            columns[index] = row.get(index + 1); // Strangely, row is 1-based
                        }

                        if (aArkMap.containsKey(ark)) {
                            columns[columnCount] = constructAccessURL(aArkMap.get(ark));
                        }
                    } else {
                        columns = new String[columnCount];

                        for (int index = 0; index < columnCount; index++) {
                            if (accessUrlIndex != index) {
                                columns[index] = row.get(index + 1); // Strangely, row is 1-based
                            } else if (aArkMap.containsKey(ark)) {
                                columns[index] = constructAccessURL(aArkMap.get(ark));
                            }
                        }
                    }

                    writer.writeRow(columns);
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
            return aHeader.getIndex(CsvItem.IIIF_ACCESS_URL_HEADER) - 1; // Strangely, header index is 1-based
        } catch (final CsvException details) {
            return -1; // Seems odd to throw an exception for this
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
