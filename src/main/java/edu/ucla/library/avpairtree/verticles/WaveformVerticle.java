
package edu.ucla.library.avpairtree.verticles;

import static edu.ucla.library.avpairtree.AvPtConstants.WAVEFORM_CONSUMER;
import static info.freelibrary.util.Constants.SPACE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.avpairtree.AvPtUtils;
import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * A verticle that transforms audio files into audiowaveform data.
 */
public final class WaveformVerticle extends AbstractVerticle {

    /**
     * The waveform verticle's logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformVerticle.class, MessageCodes.BUNDLE);

    /**
     * The name of the audiowaveform executable.
     */
    private static final String AUDIOWAVEFORM = "audiowaveform";

    /**
     * The template string for AWS S3 object keys. The slot is: item ARK.
     */
    private static final String S3_OBJECT_KEY_TEMPLATE = "{}/audiowaveform.dat";

    /**
     * The waveform source directory
     */
    private String mySourceDir;

    @Override
    public void start(final Promise<Void> aPromise) {
        final JsonObject config = config();
        final String[] cmd = { "which", AUDIOWAVEFORM };
        final String cmdline = String.join(SPACE, cmd);

        LOGGER.debug(MessageCodes.AVPT_011, WaveformVerticle.class.getSimpleName(), Thread.currentThread().getName());

        // Make sure that audiowaveform is installed on the system
        try {
            final Process which = new ProcessBuilder(cmd).start();
            final int exitValue = which.waitFor();
            final String cmdResult;

            try (InputStream stdIn = which.getInputStream()) {
                final String input = new String(stdIn.readAllBytes());

                cmdResult = LOGGER.getMessage(MessageCodes.AVPT_015, cmdline, exitValue, input);
            }

            if (0 == exitValue) {
                LOGGER.debug(cmdResult);
            } else {
                LOGGER.error(cmdResult);
                aPromise.fail(cmdResult);

                return;
            }
        } catch (final IOException | InterruptedException details) {
            final String startErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_016, cmdline, details);

            LOGGER.error(startErrorMsg);
            aPromise.fail(details);

            return;
        }

        mySourceDir = config.getString(Config.SOURCE_DIR);

        vertx.eventBus().<CsvItem>consumer(getClass().getName()).handler(this::handle);

        aPromise.complete();
    }

    /**
     * Transforms the source audio file at the given path into audiowaveform data, compresses and uploads that data to
     * S3, and replies to the message with the URL for the compressed data. If either the transformation, compression,
     * or upload fails, sends back error details.
     *
     * @param aMessage A message with the file path of the audio file to transform
     */
    private void handle(final Message<CsvItem> aMessage) {
        try {
            final CsvItem csvItem = aMessage.body();
            final Path audioFilePath = AvPtUtils.getInputFilePath(csvItem, mySourceDir);

            getAudiowaveform(audioFilePath).onSuccess(data -> {
                final String ark = csvItem.getItemARK();
                final String s3ObjectKey = StringUtils.format(S3_OBJECT_KEY_TEMPLATE, ark);
                final DeliveryOptions options =
                        new DeliveryOptions().addHeader("key", s3ObjectKey).addHeader("contentEncoding", "gzip");

                try {
                    final byte[] compressedData = gzip(data);

                    // Store the compressed audiowaveform data on S3
                    vertx.eventBus().<String>request(WAVEFORM_CONSUMER, compressedData, options).onSuccess(result -> {
                        // Reply with a JsonObject associating the item ARK with the URL for the audiowaveform data
                        final String audiowaveformURL = result.body();
                        final JsonObject response = new JsonObject().put(csvItem.getItemARK(), audiowaveformURL);

                        aMessage.reply(response);
                    }).onFailure(details -> aMessage.fail(Op.ERROR_CODE, details.getMessage()));
                } catch (final IOException details) {
                    aMessage.fail(Op.ERROR_CODE, details.getMessage());
                }
            }).onFailure(details -> {
                aMessage.fail(Op.ERROR_CODE, details.getMessage());
            });
        } catch (final IOException details) {
            aMessage.fail(Op.ERROR_CODE, details.getMessage());
        }
    }

    /**
     * Transforms the source audio file at the given path into binary audiowaveform data.
     *
     * @param anAudioFilePath The path to the audio file to transform
     * @return A Future that is completed with a byte array containing the audiowaveform data
     * @throws IOException if an I/O error occurs during the execution of the audiowaveform program
     */
    private Future<byte[]> getAudiowaveform(final Path anAudioFilePath) throws IOException {
        final Promise<byte[]> asyncResult = Promise.promise();
        final String[] cmd = { AUDIOWAVEFORM, "--input-filename", anAudioFilePath.toString(), "--output-format", "dat",
            "--bits", "8" };
        final String cmdline = String.join(SPACE, cmd);

        try {
            final Process audiowaveform = new ProcessBuilder(cmd).start();

            // Unless we read its output before calling `onExit()`, the audiowaveform process will stay asleep until it
            // receives an interrupt signal
            final byte[] stdout = audiowaveform.getInputStream().readAllBytes();
            final String stderr = new String(audiowaveform.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            audiowaveform.onExit().thenAccept(process -> {
                final int exitValue = process.exitValue();

                if (0 == exitValue) {
                    for (final String line : stderr.split("\\r?\\n")) {
                        LOGGER.debug(line);
                    }
                    // Redact the binary audiowaveform data for logging
                    LOGGER.debug(MessageCodes.AVPT_015, cmdline, exitValue, "[binary audiowaveform data]");

                    asyncResult.complete(stdout);
                } else {
                    asyncResult.fail(LOGGER.getMessage(MessageCodes.AVPT_015, cmdline, exitValue, stderr));
                }
            });
        } catch (final IOException details) { // NOPMD - PMD doesn't like wrapped exceptions with same type
            throw new IOException(LOGGER.getMessage(MessageCodes.AVPT_016, cmdline, details));
        }

        return asyncResult.future();
    }

    /**
     * Compresses the data in the given byte array to GZIP format.
     *
     * @param aByteArray The uncompressed data
     * @return The compressed data
     * @throws IOException if an I/O error occurs during the data compression
     */
    private byte[] gzip(final byte[] aByteArray) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (GZIPOutputStream gz = new GZIPOutputStream(outputStream)) {
            gz.write(aByteArray);
            gz.finish();

            return outputStream.toByteArray();
        } catch (final IOException details) { // NOPMD - PMD doesn't like wrapped exceptions with same type
            throw new IOException(LOGGER.getMessage(MessageCodes.AVPT_023, details));
        }
    }
}
