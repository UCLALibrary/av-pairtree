package edu.ucla.library.avpairtree.verticles;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
        final ProcessBuilder pb = new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.PIPE);

        final String configErrorMsg;

        // Make sure that audiowaveform is installed on the system

        try {
            final Process which = pb.start();
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
     * Transforms the source audio file at the given path into audiowaveform data, uploads that data to S3, and replies
     * to the message with the URL for the data. If either the transformation or upload fails, sends back error details.
     *
     * @param aMessage A message with the file path of the audio file to transform
     */
    private void handle(final Message<CsvItem> aMessage) {
        try {
            final CsvItem csvItem = aMessage.body();
            final Path audioFilePath = AvPtUtils.getInputFilePath(csvItem, mySourceDir);

            audiowaveform(audioFilePath).onSuccess(s3ObjectData -> {
                final String ark = csvItem.getItemARK();
                final String s3ObjectKey = StringUtils.format(S3_OBJECT_KEY_TEMPLATE, ark);
                final PutObjectRequest req = PutObjectRequest.builder().bucket(myS3Bucket).key(s3ObjectKey).build();
                final AsyncRequestBody body = AsyncRequestBody.fromByteBuffer(s3ObjectData);

                // Store the audiowaveform data on S3
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
     * @return A Future that is completed with a ByteBuffer containing the audiowaveform data
     * @throws IOException if an I/O error occurs during the execution of the audiowaveform program
     */
    private Future<ByteBuffer> audiowaveform(final Path anAudioFilePath) throws IOException {
        final Promise<ByteBuffer> asyncResult = Promise.promise();
        final String[] cmd = { AUDIOWAVEFORM, "--input-filename", anAudioFilePath.toString(), "--output-format", "dat",
            "--bits", "8" };
        final String cmdline = String.join(SPACE, cmd);
        final ProcessBuilder pb = new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.PIPE);

        try {
            pb.start().onExit().thenAccept(subprocess -> {
                try (InputStream stdout = subprocess.getInputStream();
                        InputStream stderr = subprocess.getErrorStream()) {

                    final int exitValue = subprocess.exitValue();

                    if (0 == exitValue) {
                        // Redact the binary audiowaveform data for logging
                        final String cmdResultMsg = LOGGER.getMessage(MessageCodes.AVPT_015, cmdline, exitValue,
                                "[binary audiowaveform data]");

                        LOGGER.debug(cmdResultMsg);

                        asyncResult.complete(ByteBuffer.wrap(stdout.readAllBytes()));
                    } else {
                        final String errorOutput = new String(stderr.readAllBytes());

                        asyncResult.fail(LOGGER.getMessage(MessageCodes.AVPT_015, cmdline, exitValue, errorOutput));
                    }
                } catch (final IOException details) {
                    asyncResult.fail(LOGGER.getMessage(MessageCodes.AVPT_016, cmdline, details));
                }
            });
        } catch (final IOException details) {
            throw new IOException(LOGGER.getMessage(MessageCodes.AVPT_016, cmdline, details));
        }

        return asyncResult.future();
    }
}
