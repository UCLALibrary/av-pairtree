package edu.ucla.library.avpairtree;

import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import io.vertx.core.shareddata.AsyncMap;

/**
 * A class that adds and removes jobs from a shared map
 */
public class JobsQueue {
    /**
     * The watcher verticle's logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsQueue.class, MessageCodes.BUNDLE);

    /**
     * Name of shared map
     */
    private static final String MY_JOBS_QUEUE = "jobs-queue";

    /**
     * Copy of VertX instance
     */
    private final Vertx myVertx;

    /**
     * Accesses the jobs queue shared data map
     *
     * @param aVertx A Vert.x instance
     */
    public JobsQueue(final Vertx aVertx) {
        myVertx = aVertx;
    }

    /**
     * Add job to jobs queue
     *
     * @param aItem A string of the map name
     * @return A future completing task with the map result
     */
    public Future<Void> addJobToQueue(final CsvItem aItem) {
        return getSharedMap(MY_JOBS_QUEUE).compose(jobsMap -> {
            return putJobInMap(jobsMap, aItem);
        });
    }

    /**
     * Remove jobs from job queue
     *
     * @param aArk A string of the map name
     * @return A future completing task with the map result
     */
    public Future<Void> removeJobInQueue(final String aArk) {
        return getSharedMap(MY_JOBS_QUEUE).compose(map -> {
            return getKeys(map).compose(jobs -> {
                return deleteKeyInMap(map, jobs, aArk);
            });
        });
    }

    /**
     * Retrieved shared local async data map.
     *
     * @param aMapName A string of the map name
     * @return A future completing task with the map result
     */
    private Future<AsyncMap<String, CsvItem>> getSharedMap(final String aMapName) {
        final Promise<AsyncMap<String, CsvItem>> promise = Promise.promise();

        myVertx.sharedData().<String, CsvItem>getLocalAsyncMap(aMapName, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, CsvItem> jobsQueue = getMap.result();
                promise.complete(jobsQueue);
            } else {
                LOGGER.error(MessageCodes.AVPT_027);
                promise.fail(getMap.cause());
            }
        });

        return promise.future();
    }

    /**
     *
     * @param aJobsQueue A string of the map name
     * @param aItem A CsvItem
     * @return A future completing task
     */
    private Future<Void> putJobInMap(final AsyncMap<String, CsvItem> aJobsQueue, final CsvItem aItem) {
        final Promise<Void> promise = Promise.promise();

        aJobsQueue.put(aItem.getItemARK(), aItem, put -> {
            if (put.succeeded()) {
                promise.complete();
            } else {
                LOGGER.error(MessageCodes.AVPT_028);
                promise.fail(put.cause());
            }
        });

        return promise.future();
    }

    /**
     *
     * @param aJobsQueue A string of the map name
     * @return A future completing task with the Jobs
     */
    private Future<Set<String>> getKeys(final AsyncMap<String, CsvItem> aJobsQueue) {
        final Promise<Set<String>> promise = Promise.promise();
        aJobsQueue.keys(keyCheck -> {
            if (keyCheck.succeeded()) {
                final Set<String> jobs = keyCheck.result();
                promise.complete(jobs);
            } else {
                LOGGER.error(MessageCodes.AVPT_029);
                promise.fail(keyCheck.cause());
            }
        });

        return promise.future();
    }

    /**
     *
     * @param aJobsQueue A string of the map name
     * @param aJobs A set of the job being removed
     * @param aArk A string of the ark being removed
     * @return A future completing task
     */
    private Future<Void> deleteKeyInMap(final AsyncMap<String, CsvItem> aJobsQueue,
                            final Set<String> aJobs, final String aArk) {

        final Promise<Void> promise = Promise.promise();

        if (aJobs.contains(aArk)) {
            aJobsQueue.remove(aArk, deleteJob -> {
                if (deleteJob.succeeded()) {
                    promise.complete();
                } else {
                    LOGGER.error(MessageCodes.AVPT_031);
                    promise.fail(deleteJob.cause());
                }
            });
        } else {
            LOGGER.error(MessageCodes.AVPT_030);
            promise.fail(MessageCodes.AVPT_030);
        }

        return promise.future();
    }
}
