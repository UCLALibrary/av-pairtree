package edu.ucla.library.avpairtree.handlers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

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
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest.Builder;

/**
 * A consumer of waveform data that uses Localstack S3 as storage.
 */
public class LocalstackS3WaveformConsumer implements Handler<Message<byte[]>> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LocalstackS3WaveformConsumer.class, MessageCodes.BUNDLE);

    /**
     * The key of the required "key" message header.
     */
    private static final String KEY = "key";

    /**
     * The key of the optional "contentEncoding" message header.
     */
    private static final String CONTENT_ENCODING = "contentEncoding";

    private final String myS3BucketName;

    private final String myS3ObjectUrlTemplate;

    private final S3AsyncClient myS3Client;

    /**
     * Creates a new consumer.
     *
     * @param aConfig A configuration that specifies the base URL of the S3 service and the name of the S3 bucket to
     *        create
     * @throws IllegalStateException If any required configuration info is missing
     */
    public LocalstackS3WaveformConsumer(final JsonObject aConfig) throws IllegalStateException {
        final String configErrorMsg;
        final String s3BucketName = aConfig.getString(Config.AUDIOWAVEFORM_S3_BUCKET);
        final String s3EndpointURL = aConfig.getString(Config.AWS_ENDPOINT_URL);

        if (s3BucketName == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_020);

            LOGGER.error(configErrorMsg);
            throw new IllegalStateException(configErrorMsg);
        } else if (s3EndpointURL == null) {
            configErrorMsg = LOGGER.getMessage(MessageCodes.AVPT_025);

            LOGGER.error(configErrorMsg);
            throw new IllegalStateException(configErrorMsg);
        }

        myS3BucketName = s3BucketName;
        myS3ObjectUrlTemplate = s3EndpointURL + "/" + s3BucketName + "/{}";
        myS3Client = S3AsyncClient.builder().endpointOverride(URI.create(s3EndpointURL)).build();
    }

    /**
     * Puts the waveform data on Localstack S3, using the key and content encoding specified in the message headers, and
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

    /**
     * Deletes all the objects in the S3 bucket whose name was passed to the constructor. This assumes that there are
     * not more than 1000 objects in the bucket.
     *
     * @return A Future that will be completed when the object deletions are complete
     */
    public Future<Void> clearBucket() {
        final Promise<Void> promise = Promise.promise();
        final ListObjectsV2Request listRequest = ListObjectsV2Request.builder().bucket(myS3BucketName).build();

        myS3Client.listObjectsV2(listRequest).whenComplete((resp, err) -> {
            if (resp != null) {
                if (!resp.isTruncated()) {
                    final Collection<ObjectIdentifier> s3ObjectIds = resp.contents().parallelStream().map(s3Object -> {
                        return ObjectIdentifier.builder().key(s3Object.key()).build();
                    }).collect(Collectors.toList());
                    final DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder().bucket(myS3BucketName)
                            .delete(Delete.builder().objects(s3ObjectIds).build()).build();

                    myS3Client.deleteObjects(deleteRequest).whenComplete((resp2, err2) -> {
                        if (resp2 != null) {
                            promise.complete();
                        } else {
                            promise.fail(err2);
                        }
                    });
                } else {
                    // Assume that we're not using more than 1000 objects for testing
                    promise.fail("Clearing Localstack S3 buckets with more than 1000 objects is not yet implemented");
                }
            } else {
                promise.fail(err);
            }
        });

        return promise.future();
    }

    /**
     * Deletes the S3 bucket whose name was passed to the constructor.
     *
     * @return A Future that will be completed when the bucket deletion is complete
     */
    public Future<Void> deleteBucket() {
        final Promise<Void> promise = Promise.promise();
        final DeleteBucketRequest request = DeleteBucketRequest.builder().bucket(myS3BucketName).build();

        myS3Client.deleteBucket(request).whenComplete((resp, err) -> {
            if (resp != null) {
                promise.complete();
            } else {
                promise.fail(err);
            }
        });

        return promise.future();
    }

}
