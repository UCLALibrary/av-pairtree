
package edu.ucla.library.avpairtree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A request queue for event bus requests.
 */
public final class RequestQueue {

    /** A constant for a single instance of request in flight. */
    private static final int SINGLE_INSTANCE = 1;

    /** The internal queue. */
    private final Deque<QueuedJob<?>> myQueue = new ArrayDeque<>();

    /** The number of requests allowed in-flight for a single queue. */
    private final int myMaxInFlight;

    /** The number of requests that are currently in-flight. */
    private int myInFlightCount;

    /**
     * Creates a new request queue.
     *
     * @param aMaxInFlight The maximum of in-flight requests that the queue should allow
     */
    public RequestQueue(final int aMaxInFlight) {
        if (aMaxInFlight < SINGLE_INSTANCE) {
            throw new IllegalArgumentException("Maximum in-flight count must be at least one");
        }

        myMaxInFlight = aMaxInFlight;
    }

    /**
     * Enqueue an operation. The supplier is not invoked until the queue has capacity, so the asynchronous operation has
     * not started yet.
     *
     * @param <T> The type of job to be performed
     * @param aJob A queued request
     * @return A future that represents the work to be done
     */
    public <T> Future<T> enqueue(final Supplier<Future<T>> aJob) {
        final Promise<T> promise = Promise.promise();

        myQueue.addLast(new QueuedJob<>(aJob, promise));
        pumpQueue();

        return promise.future();
    }

    /**
     * Pump the queue to start the next job.
     */
    private void pumpQueue() {
        while (myInFlightCount < myMaxInFlight && !myQueue.isEmpty()) {
            myInFlightCount++;
            myQueue.removeFirst().start();
        }
    }

    /**
     * A queued job.
     *
     * @param <T> The type of job being queued
     */
    private final class QueuedJob<T> {

        /** The job supplier. */
        private final Supplier<Future<T>> myJob;

        /** The promise that the work gets done. */
        private final Promise<T> myPromise;

        /**
         * Creates a queued job.
         *
         * @param aJob The job to queue
         * @param aPromise The promise that the job gets processed
         */
        private QueuedJob(final Supplier<Future<T>> aJob, final Promise<T> aPromise) {
            myJob = aJob;
            myPromise = aPromise;
        }

        /**
         * Starts the queue's processing.
         */
        @SuppressWarnings({ "PMD.AvoidCatchingGenericException" })
        private void start() {
            try {
                myJob.get().onComplete(result -> {
                    myInFlightCount--;

                    if (result.succeeded()) {
                        myPromise.complete(result.result());
                    } else {
                        myPromise.fail(result.cause());
                    }

                    pumpQueue();
                });
            } catch (final Exception details) {
                myInFlightCount--;
                myPromise.fail(details);
                pumpQueue();
            }
        }
    }
}
