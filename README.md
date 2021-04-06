# A/V Pairtree

This project processes A/V files into a collection of Pairtree structures.

## Pre-requisites

The only hard requirement is that you need a [JDK (>= 11)](https://adoptopenjdk.net/) installed and configured.

There are two sets of build instructions: one for systems with [Maven](https://maven.apache.org/) pre-installed and one for systems without Maven.

## Building and testing locally without Maven pre-installed

To build the project the first time, type:

    ./mvnw validate verify

To run the service locally, type:

    ./mvnw -Plive test

These will download a version of Maven for you, build the project, and then proceed to run the project in test mode.

To process one of the test CSVs, you can copy a CSV file from `src/test/resources/csvs/` into `src/test/resources/csvs/watched`.

## Building and testing locally with Maven pre-installed

To build the project the first time, type:

    mvn validate verify

To run the service locally, type:

    mvn -Plive test

These will build and run the project in test mode.

To process one of the test CSVs, you can copy a CSV file from `src/test/resources/csvs/` into `src/test/resources/csvs/watched`.

## Additional instructions

The `validate` argument only needs to be supplied to the mvn(w) command on the first run. After that, `mvn(w) verify` (or `mvn(w) package`) will work fine. Also, the build automatically happens when you run `mvn(w) -Plive test` so you don't need to repeat both steps just to run a test after the initial run.

## Running in production

To run av-pairtree from the Jar file, one needs to type the following:

    java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -Dvertx-config-path=config.properties -jar target/av-pairtree-0.0.1-SNAPSHOT.jar run edu.ucla.library.avpairtree.verticles.MainVerticle

The application is configured by the value of `vertx-config-path`, which in the example above is a config file residing in the same directory as the Jar file.

## Contact

We use an internal ticketing system, but we've left the [GitHub issues](https://github.com/UCLALibrary/av-pairtree/issues) open in case you'd like to file a ticket or make a suggestion.
