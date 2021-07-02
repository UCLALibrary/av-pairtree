package edu.ucla.library.avpairtree.verticles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
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
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * A verticle that transforms audio files into audiowaveform data.
 */
public final class WaveformVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformVerticle.class, MessageCodes.BUNDLE);

    // The name of the audiowaveform executable.
    private static final String AUDIOWAVEFORM = "audiowaveform";

    // The template string for AWS S3 object keys. The slot is: item ARK.
    private static final String S3_OBJECT_KEY_TEMPLATE = "{}/audiowaveform.dat";

    private static final String SPACE = " ";

    private String myAwsDefaultRegion;

    private S3AsyncClient myS3Client;

    private String myS3Bucket;

    private String myS3ObjectUrlTemplate;

    private String mySourceDir;

    @Override
    public void start(final Promise<Void> aPromise) {
        final JsonObject config = config();
        final String[] cmd = { "which", AUDIOWAVEFORM };
        final String cmdline = String.join(SPACE, cmd);
        final String configErrorMsg;

        LOGGER.debug(MessageCodes.AVPT_011, WaveformVerticle.class.getSimpleName(), Thread.currentThread().getName());

        // Make sure that audiowaveform is installed on the system

        try {
            final Process which = new ProcessBuilder(cmd).start();
            final int exitValue = which.waitFor();
            final InputStream stdout;
            final String cmdResult;

            stdout = which.getInputStream();
            cmdResult =
                    LOGGER.getMessage(MessageCodes.AVPT_015, cmdline, exitValue, new String(stdout.readAllBytes()));
            stdout.close();

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

        // Make sure that configuration and credentials for AWS S3 have been provided

        myAwsDefaultRegion = config.getString("AWS_DEFAULT_REGION");

        if (myAwsDefaultRegion == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_018);

            LOGGER.error(configErrorMsg);
            aPromise.fail(configErrorMsg);

            return;
        }

        if (config.getString(ProfileProperty.AWS_ACCESS_KEY_ID.toUpperCase()) == null ||
                config.getString(ProfileProperty.AWS_SECRET_ACCESS_KEY.toUpperCase()) == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_017);

            LOGGER.error(configErrorMsg);
            aPromise.fail(configErrorMsg);

            return;
        }

        myS3Bucket = config.getString(Config.AUDIOWAVEFORM_S3_BUCKET);

        if (myS3Bucket == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_020);

            LOGGER.error(configErrorMsg);
            aPromise.fail(configErrorMsg);

            return;
        }

        myS3ObjectUrlTemplate = config.getString(Config.AUDIOWAVEFORM_S3_OBJECT_URL_TEMPLATE);

        if (myS3ObjectUrlTemplate == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_021);

            LOGGER.error(configErrorMsg);
            aPromise.fail(configErrorMsg);

            return;
        }

        myS3Client = S3AsyncClient.builder().region(Region.of(myAwsDefaultRegion)).build();

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

            audiowaveform(audioFilePath).onSuccess(data -> {
                final String ark = csvItem.getItemARK();
                final String s3ObjectKey = StringUtils.format(S3_OBJECT_KEY_TEMPLATE, ark);
                final PutObjectRequest req =
                        PutObjectRequest.builder().bucket(myS3Bucket).key(s3ObjectKey).contentEncoding("gzip").build();

                try {
                    final byte[] compressedData = gzip(data);
                    final AsyncRequestBody body = AsyncRequestBody.fromBytes(compressedData);

                    // Store the compressed audiowaveform data on S3
                    myS3Client.putObject(req, body).whenComplete((resp, err) -> {
                        if (resp != null) {
                            // Success!
                            final String audiowaveformURL = StringUtils.format(myS3ObjectUrlTemplate,
                                    URLEncoder.encode(s3ObjectKey, StandardCharsets.UTF_8));

                            // Reply with a JsonObject associating the item ARK with the URL for the audiowaveform data
                            aMessage.reply(new JsonObject().put(csvItem.getItemARK(), audiowaveformURL));
                        } else {
                            final String s3ErrorMsg =
                                    LOGGER.getMessage(MessageCodes.AVPT_022, s3ObjectKey, err.getMessage());

                            // Since the sender (WatcherVerticle) just logs all errors, should be okay to use a single
                            // failureCode for all errors
                            aMessage.fail(Op.ERROR_CODE, s3ErrorMsg);
                        }
                    });
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
    private Future<byte[]> audiowaveform(final Path anAudioFilePath) throws IOException {
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
        } catch (final IOException details) {
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
        } catch (final IOException details) {
            throw new IOException(LOGGER.getMessage(MessageCodes.AVPT_023, details));
        }
    }
}
