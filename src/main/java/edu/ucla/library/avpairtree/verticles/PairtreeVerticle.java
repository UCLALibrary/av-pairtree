
package edu.ucla.library.avpairtree.verticles;

import java.io.File;
import java.nio.file.Path;

import info.freelibrary.util.Constants;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import info.freelibrary.pairtree.Pairtree;
import info.freelibrary.pairtree.PairtreeException;
import info.freelibrary.pairtree.PairtreeFactory;
import info.freelibrary.pairtree.PairtreeObject;
import info.freelibrary.pairtree.PairtreeUtils;

import edu.ucla.library.avpairtree.Config;
import edu.ucla.library.avpairtree.CsvItem;
import edu.ucla.library.avpairtree.MessageCodes;
import edu.ucla.library.avpairtree.Op;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A verticle that stores a media file in a Pairtree directory structure.
 */
public class PairtreeVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PairtreeVerticle.class, MessageCodes.BUNDLE);

    @Override
    public void start(final Promise<Void> aPromise) {
        final Vertx vertx = getVertx();
        final String ptPrefix = config().getString(Config.PAIRTREE_PREFIX);
        final File ptDirectory = new File(config().getString(Config.OUTPUT_DIR));
        final PairtreeFactory ptFactory = new PairtreeFactory(vertx);

        vertx.eventBus().<CsvItem>consumer(getClass().getName()).handler(message -> {
            try {
                final CsvItem csvItem = message.body();
                final File itemDirectory = new File(ptDirectory, csvItem.getPathRoot());
                final Pairtree pairtree = ptFactory.getPrefixedPairtree(ptPrefix, itemDirectory);
                final String filePath = getFilePath(csvItem.getFilePath());

                createIfNeeded(pairtree).onSuccess(creation -> {
                    vertx.fileSystem().exists(filePath).onSuccess(exists -> {
                        final PairtreeObject ptObject = pairtree.getObject(csvItem.getItemARK());
                        final String id = PairtreeUtils.encodeID(csvItem.getItemARK());
                        final String extension = config().getString(Config.ENCODING_FORMAT);

                        removeIfNeeded(ptObject).onSuccess(clean -> {
                            ptObject.put(id + Constants.PERIOD + extension, filePath, put -> {
                                if (put.succeeded()) {
                                    message.reply(csvItem.setProcessingStatus(true));
                                } else {
                                    LOGGER.error(put.cause(), put.cause().getMessage());
                                    message.fail(Op.ERROR_CODE, put.cause().getMessage());
                                }
                            });
                        }).onFailure(error -> {
                            LOGGER.error(error, error.getMessage());
                            message.fail(Op.ERROR_CODE, error.getMessage());
                        });
                    }).onFailure(error -> {
                        LOGGER.error(error, error.getMessage());
                        message.fail(Op.ERROR_CODE, error.getMessage());
                    });
                }).onFailure(error -> {
                    LOGGER.error(error, error.getMessage());
                    message.fail(Op.ERROR_CODE, error.getMessage());
                });
            } catch (final PairtreeException details) {
                LOGGER.error(details, details.getMessage());
                message.fail(Op.ERROR_CODE, details.getMessage());
            }
        });

        aPromise.complete();

    }

    /**
     * A function to remove a pre-existing Pairtree object, if necessary, so that a new one can be written.
     *
     * @param aPtObject A Pairtree object
     * @return A future result
     */
    private Future<Void> removeIfNeeded(final PairtreeObject aPtObject) {
        final Promise<Void> promise = Promise.promise();

        // This could be abstracted away some by vertx-pairtree changes
        aPtObject.exists(exists -> {
            if (exists.succeeded()) {
                if (exists.result()) {
                    aPtObject.delete(delete -> {
                        if (delete.succeeded()) {
                            promise.complete();
                        } else {
                            promise.fail(delete.cause());
                        }
                    });
                } else {
                    promise.complete();
                }
            } else {
                promise.fail(exists.cause());
            }
        });

        return promise.future();
    }

    /**
     * A convenience wrapper to reduce callback hell until vertx-pairtree supports futures.
     *
     * @param aPairtree A pairtree
     * @return A future pairtree
     */
    private Future<Void> createIfNeeded(final Pairtree aPairtree) {
        final Promise<Void> promise = Promise.promise();

        aPairtree.createIfNeeded(create -> {
            if (create.succeeded()) {
                promise.complete();
            } else {
                promise.fail(create.cause());
            }
        });

        return promise.future();
    }

    /**
     * Gets the file path of the file to put into the Pairtree.
     *
     * @param aFilePath A media file path
     * @return The file path of the file to put into the Pairtree
     */
    private String getFilePath(final String aFilePath) {
        if (aFilePath.startsWith(Constants.SLASH)) {
            return aFilePath;
        } else {
            return Path.of(config().getString(Config.SOURCE_DIR), aFilePath).toAbsolutePath().toString();
        }
    }
}
