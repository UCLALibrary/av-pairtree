
package edu.ucla.library.avpairtree.verticles;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                        final Promise<Message<Object>> promise = Promise.promise();

                        // Insert our promise into the queue before we do any future work
                        futures.add(promise.future());

                        convertAudioFile(item, options).onSuccess(convertedItem -> {
                            storeAudioFile(promise, convertedItem, options);
                        }).onFailure(error -> promise.fail(error));
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

                        LOGGER.info(MessageCodes.AVPT_009, ark, item.getFilePath());
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
     * Sends the audio file to the PairtreeVerticle for placement in the tree.
     *
     * @param aPromise A promise that the audio file will be stored in the Pairtree
     * @param aCsvItem A CSV item whose file should be stored
     * @param aConfig A delivery configuration
     * @return The stored CSV item
     */
    private void storeAudioFile(final Promise<Message<Object>> aPromise, final CsvItem aCsvItem,
        final DeliveryOptions aConfig) {
        getVertx().eventBus().request(PairtreeVerticle.class.getName(), aCsvItem, aConfig).onSuccess(result -> {
            final CsvItem csvItem = (CsvItem) result.body();

            // Clean up our converted file after it has been successfully put into the Pairtree
            vertx.fileSystem().delete(csvItem.getFilePath()).onComplete(deletion -> {
                if (deletion.succeeded()) {
                    aPromise.complete(result);
                } else {
                    aPromise.fail(deletion.cause());
                }
            });
        }).onFailure(error -> aPromise.fail(error));
    }

    /**
     * Converts the CSV item's audio file.
     *
     * @param aCsvItem A CSV item
     * @param aConfig A delivery configuration
     * @return A future with the converted audio file
     */
    private Future<CsvItem> convertAudioFile(final CsvItem aCsvItem, final DeliveryOptions aConfig) {
        final Promise<CsvItem> promise = Promise.promise();

        getVertx().eventBus().request(ConverterVerticle.class.getName(), aCsvItem, aConfig).onSuccess(conversion -> {
            promise.complete((CsvItem) conversion.body());
        }).onFailure(error -> promise.fail(error));

        return promise.future();
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
                            columns[columnCount] = encodeAccessURL(aArkMap.get(ark));
                        }
                    } else {
                        columns = new String[columnCount];

                        for (int index = 0; index < columnCount; index++) {
                            if (accessUrlIndex != index) {
                                columns[index] = row.get(index + 1); // Strangely, row is 1-based
                            } else if (aArkMap.containsKey(ark)) {
                                columns[index] = encodeAccessURL(aArkMap.get(ark));
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
     * Encodes the Pairtree path for the A/V server's access URL.
     *
     * @param aCsvItem An item from the CSV file
     * @return An encoded path for the A/V server's access URL
     */
    private String encodeAccessURL(final CsvItem aCsvItem) {
        final String arkPrefix = config().getString(Config.PAIRTREE_PREFIX);
        final String ark = aCsvItem.getItemARK();
        final String pathARK = ark.replace(arkPrefix, Constants.EMPTY); // Strip ARK prefix
        final String slug = aCsvItem.getPathRoot();
        final String encodedARK = PairtreeUtils.encodeID(ark);
        final String fileExt = Constants.PERIOD + config().getString(Config.ENCODING_FORMAT);
        final String accessUrlPattern = config().getString(Config.ACCESS_URL_PATTERN, "{}");
        final String ptPath = PairtreeUtils.mapToPtPath(slug + Constants.SLASH + Pairtree.ROOT, pathARK, pathARK);
        final String ptFile = (ptPath + Constants.SLASH + encodedARK + fileExt).replace(Constants.PLUS, "%2B");
        final String accessURL = StringUtils.format(accessUrlPattern, ptFile);

        LOGGER.debug(MessageCodes.AVPT_010, ark, accessURL);

        return accessURL;
    }
}
