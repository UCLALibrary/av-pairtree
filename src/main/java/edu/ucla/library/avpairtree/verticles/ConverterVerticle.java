
package edu.ucla.library.avpairtree.verticles;

import static edu.ucla.library.avpairtree.AvPtConstants.SYSTEM_TMP_DIR;

import java.nio.file.Path;

import info.freelibrary.util.Constants;
import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.avpairtree.AvPtUtils;
import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

/**
 * A verticle that converts archival media files into derivative media files.
 */
public class ConverterVerticle extends AbstractVerticle {

    /**
     * A scratch space into which to write converted audio files.
     */
    public static final String SCRATCH_SPACE = "av-pairtree-";

    /**
     * The logger used for messages from the ConverterVerticle.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ConverterVerticle.class, MessageCodes.BUNDLE);

    /**
     * The default audio codec.
     */
    private static final String DEFAULT_AUDIO_CODEC = "aac";

    /**
     * The default bit rate of the audio codec.
     */
    private static final int DEFAULT_BIT_RATE = 128_000;

    /**
     * The default audio channels.
     */
    private static final int DEFAULT_CHANNELS = 2;

    /**
     * The default sampling rate.
     */
    private static final int DEFAULT_SAMPLING_RATE = 44_100;

    /**
     * The default file encoding format.
     */
    private static final String DEFAULT_ENCODING_FORMAT = "mp4";

    @Override
    public void start(final Promise<Void> aPromise) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(Integer.MAX_VALUE);
        final JsonObject config = config();
        final String sourceDir = config.getString(Config.SOURCE_DIR);
        final Vertx vertx = getVertx();

        LOGGER.debug(MessageCodes.AVPT_011, ConverterVerticle.class.getSimpleName(), Thread.currentThread().getName());

        vertx.eventBus().<CsvItem>consumer(getClass().getName()).handler(message -> {
            final String outputFormat = config.getString(Config.ENCODING_FORMAT, DEFAULT_ENCODING_FORMAT);
            final CsvItem csvItem = message.body();

            try {
                final Path inputFilePath = AvPtUtils.getInputFilePath(csvItem, sourceDir);
                final Path outputFilePath = getOutputFilePath(inputFilePath, outputFormat);
                final EncodingAttributes encoding = new EncodingAttributes();
                final AudioAttributes audio = new AudioAttributes();
                final Encoder encoder = new Encoder();

                audio.setCodec(config.getString(Config.AUDIO_CODEC, DEFAULT_AUDIO_CODEC));
                audio.setBitRate(config.getInteger(Config.BIT_RATE, DEFAULT_BIT_RATE));
                audio.setChannels(config.getInteger(Config.CHANNELS, DEFAULT_CHANNELS));
                audio.setSamplingRate(config.getInteger(Config.SAMPLING_RATE, DEFAULT_SAMPLING_RATE));

                encoding.setOutputFormat(outputFormat);
                encoding.setAudioAttributes(audio);
                encoding.setEncodingThreads(config.getInteger(Config.ENCODING_THREADS));

                encoder.encode(new MultimediaObject(inputFilePath.toFile()), outputFilePath.toFile(), encoding);
                csvItem.setFilePath(outputFilePath.toAbsolutePath().toString());

                // Send our converted file to the Pairtree verticle for placement in the A/V Pairtree
                vertx.eventBus().request(PairtreeVerticle.class.getName(), csvItem, options).onSuccess(result -> {
                    // Clean up our converted file after it has been successfully put into the Pairtree
                    vertx.fileSystem().delete(outputFilePath.toAbsolutePath().toString()).onComplete(deletion -> {
                        if (deletion.succeeded()) {
                            // If our scratch space file was cleaned up, report the success back to the watcher
                            message.reply(result.body());
                        } else {
                            LOGGER.error(deletion.cause(), deletion.cause().getMessage());
                            message.fail(Op.ERROR_CODE, deletion.cause().getMessage());
                        }
                    });
                }).onFailure(error -> {
                    LOGGER.error(error, error.getMessage());
                    message.fail(Op.ERROR_CODE, error.getMessage());
                });
            } catch (final Exception details) { // NOPMD - don't check generic exceptions
                LOGGER.error(details, details.getMessage());
                message.fail(Op.ERROR_CODE, details.getMessage());
            }
        });

        // Create an temporary scratch space for converted media files
        vertx.fileSystem().createTempDirectory(SCRATCH_SPACE).onSuccess(result -> {
            LOGGER.debug(MessageCodes.AVPT_005, result);
            aPromise.complete();
        }).onFailure(error -> aPromise.fail(error));
    }

    /**
     * Gets the output file path from available variables.
     *
     * @param aInputFilePath The path of the input audio file
     * @param aOutputFormat The output format for the audio file
     * @return A file system path for the output file
     */
    private Path getOutputFilePath(final Path aInputFilePath, final String aOutputFormat) {
        final String baseFileName = FileUtils.stripExt(aInputFilePath.getFileName().toString());
        final String outputFileName = baseFileName + Constants.PERIOD + aOutputFormat;

        return Path.of(SYSTEM_TMP_DIR, SCRATCH_SPACE, outputFileName);
    }
}
