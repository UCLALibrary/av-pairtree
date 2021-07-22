package edu.ucla.library.avpairtree.handlers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest.Builder;

/**
 * A consumer of waveform data that uses Amazon S3 as storage.
 */
public class AmazonS3WaveformConsumer implements Handler<Message<byte[]>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmazonS3WaveformConsumer.class, MessageCodes.BUNDLE);

    /**
     * The key of the required "key" message header.
     */
    private static final String KEY = "key";

    /**
     * The key of the optional "contentEncoding" message header.
     */
    private static final String CONTENT_ENCODING = "contentEncoding";

    private final String myS3ObjectUrlTemplate;

    private final String myS3BucketName;

    private final S3AsyncClient myS3Client;

    /**
     * Creates a new consumer of waveform data that uses Amazon S3 as storage.
     *
     * @param aConfig A JSON configuration
     * @throws IllegalStateException If any required Amazon S3 credentials or configuration info is missing
     */
    public AmazonS3WaveformConsumer(final JsonObject aConfig) throws IllegalStateException {
        final String configErrorMsg;
        final S3AsyncClientBuilder s3ClientBuilder = S3AsyncClient.builder();

        final String awsDefaultRegion = aConfig.getString("AWS_DEFAULT_REGION");
        final String s3BucketName = aConfig.getString(Config.AUDIOWAVEFORM_S3_BUCKET);
        final String s3EndpointURL = aConfig.getString(Config.AWS_ENDPOINT_URL);

        if (awsDefaultRegion == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_018);

            LOGGER.error(configErrorMsg);
            throw new IllegalStateException(configErrorMsg);
        } else if (aConfig.getString(ProfileProperty.AWS_ACCESS_KEY_ID.toUpperCase()) == null ||
                aConfig.getString(ProfileProperty.AWS_SECRET_ACCESS_KEY.toUpperCase()) == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_017);

            LOGGER.error(configErrorMsg);
            throw new IllegalStateException(configErrorMsg);
        } else if (s3BucketName == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_020);

            LOGGER.error(configErrorMsg);
            throw new IllegalStateException(configErrorMsg);
        }

        if (s3EndpointURL != null) {
            // LocalStack
            s3ClientBuilder.endpointOverride(URI.create(s3EndpointURL));

            myS3ObjectUrlTemplate = s3EndpointURL + "/" + s3BucketName + "/{}";
        } else {
            final String s3ObjectUrlTemplate = aConfig.getString(Config.AUDIOWAVEFORM_S3_OBJECT_URL_TEMPLATE);

            if (s3ObjectUrlTemplate == null) {
                configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_021);

                LOGGER.error(configErrorMsg);
                throw new IllegalStateException(configErrorMsg);
            }
            myS3ObjectUrlTemplate = s3ObjectUrlTemplate;
        }

        myS3BucketName = s3BucketName;
        myS3Client = s3ClientBuilder.region(Region.of(awsDefaultRegion)).build();
    }

    /**
     * Puts the waveform data on Amazon S3, using the key and content encoding specified in the message headers, and
     * replies with the URL of the object.
     *
     * @param aMessage A message containing the waveform data and headers
     * @throws IllegalArgumentException If an object key was not supplied in the message headers
     */
    @Override
    public void handle(final Message<byte[]> aMessage) throws IllegalArgumentException {
        final MultiMap headers = aMessage.headers();
        final Builder putRequestBuilder = PutObjectRequest.builder().bucket(myS3BucketName);
        final PutObjectRequest putRequest;
        final String s3ObjectKey;

        // Required
        if (headers.contains(KEY)) {
            s3ObjectKey = headers.get(KEY);
            putRequestBuilder.key(s3ObjectKey);
        } else {
            throw new IllegalArgumentException(LOGGER.getMessage(MessageCodes.AVPT_024));
        }

        // Optional
        if (headers.contains(CONTENT_ENCODING)) {
            putRequestBuilder.contentEncoding(headers.get(CONTENT_ENCODING));
        }

        putRequest = putRequestBuilder.build();

        myS3Client.putObject(putRequest, AsyncRequestBody.fromBytes(aMessage.body())).whenComplete((resp, err) -> {
            if (resp != null) {
                // Success!
                final String audiowaveformURL = StringUtils.format(myS3ObjectUrlTemplate,
                        URLEncoder.encode(s3ObjectKey, StandardCharsets.UTF_8));

                // Reply with a JsonObject associating the item ARK with the URL for the audiowaveform data
                aMessage.reply(audiowaveformURL);
            } else {
                final String s3ErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_022, s3ObjectKey, err.getMessage());

                // Since the sender (WatcherVerticle) just logs all errors, should be okay to use a single
                // failureCode for all errors
                aMessage.fail(Op.ERROR_CODE, s3ErrorMsg);
            }
        });
    }

    /**
     * Creates a S3 bucket using the name passed to the constructor.
     *
     * @return A Future that will be completed when the bucket creation is complete
     */
    public Future<Void> createBucket() {
        final Promise<Void> promise = Promise.promise();
        final CreateBucketRequest request = CreateBucketRequest.builder().bucket(myS3BucketName).build();

        myS3Client.createBucket(request).whenComplete((resp, err) -> {
            if (resp != null) {
                promise.complete();
            } else {
                promise.fail(err);
            }
        });

        return promise.future();
    }
}
