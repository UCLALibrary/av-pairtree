# A/V Pairtree

This project processes A/V files into a collection of Pairtree structures and generates waveform data (for visualization) from audio files.

## Pre-requisites

In addition to a [JDK (>= 11)](https://adoptopenjdk.net/) installed and configured, you will also need the following in order to generate waveform data for audio files:

  * the BBC's [audiowaveform](https://github.com/bbc/audiowaveform) command line tool, with the executable on your PATH; and
  * an AWS S3 bucket for storing the audio waveform data, and write credentials for the bucket (which you'll likely want to be world-readable).

There are two sets of build instructions: one for systems with [Maven](https://maven.apache.org/) pre-installed and one for systems without Maven.

## Building and testing locally without Maven pre-installed

To build the project the first time, type:

    ./mvnw validate && ./mvnw verify

To run the service locally, type:

    ./mvnw -Plive test

These will download a version of Maven for you, build the project, and then proceed to run the project in test mode.

To process one of the test CSVs, you can copy a CSV file from `src/test/resources/csvs/` into `src/test/resources/csvs/watched`.

## Building and testing locally with Maven pre-installed

To build the project the first time, type:

    mvn validate && mvn verify

To run the service locally, type:

    mvn -Plive test

These will build and run the project in test mode.

To process one of the test CSVs, you can copy a CSV file from `src/test/resources/csvs/` into `src/test/resources/csvs/watched`.

## Additional instructions

`mvn(w) validate` only needs to be run once, in order to build the project for the first time. After that, `mvn(w) verify` (or `mvn(w) package`) will work fine. Also, the build automatically happens when you run `mvn(w) -Plive test` so you don't need to repeat both steps just to run a test after the initial run.

## Running in production

To run av-pairtree from the Jar file, one must set AWS S3 credentials and then run the JAR:

```bash
#!/bin/bash

export AUDIOWAVEFORM_S3_BUCKET=myAwsS3Bucket
export AUDIOWAVEFORM_S3_OBJECT_URL_TEMPLATE=http://example.com/{}
export AWS_ACCESS_KEY_ID=myAwsAccessKey
export AWS_DEFAULT_REGION=us-west-2
export AWS_SECRET_ACCESS_KEY=myAwsSecretKey

java \
    -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory \
    -Dvertx-config-path=config.properties \
    -jar target/av-pairtree-0.0.1-SNAPSHOT.jar run edu.ucla.library.avpairtree.verticles.MainVerticle
```

The application is configured by the value of `vertx-config-path`, which in the example above is a config file residing in the same directory as the Jar file.

## Contact

We use an internal ticketing system, but we've left the [GitHub issues](https://github.com/UCLALibrary/av-pairtree/issues) open in case you'd like to file a ticket or make a suggestion.
