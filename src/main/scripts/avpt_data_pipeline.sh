#!/bin/bash

function get_av_metadata {
    # Runs the CSV at the path provided via $1 through services-metagetter and outputs the path of the result CSV
    2>/dev/null 1>&2 \
    java -jar UCLALibrary/services-metagetter/target/build-artifact/services-metagetter-0.0.1-SNAPSHOT.jar \
        $1 ${AVPTDP_METAGETTER_MEDIA_DIRECTORY} `which ffprobe` ${AVPTDP_METAGETTER_OUTPUT_DIRECTORY} &&
    echo `strip_trailing_slash ${AVPTDP_METAGETTER_OUTPUT_DIRECTORY}`/`basename $1`
}

function change_filename_extension {
    # Change the filename extension of the provided path (piped to stdin) from .out to .csv, since festerize only looks
    # at .csv files
    read filename_dot_out &&
    filename_dot_csv=`sed -e "s/\.out$/.csv/" <<< ${filename_dot_out}`
    mv ${filename_dot_out} ${filename_dot_csv}
    echo ${filename_dot_csv}
}

function festerize_ {
    # Runs the CSV at the provided path (piped to stdin) through festerize (using the base URL provided via $1) and
    # outputs the path of the result CSV
    read csv_filename &&
    yes |
    2>/dev/null 1>&2 \
    festerize --iiif-api-version 3 --server $1 --out ${AVPTDP_FESTERIZE_OUTPUT_DIRECTORY} ${csv_filename} &&
    echo `strip_trailing_slash ${AVPTDP_FESTERIZE_OUTPUT_DIRECTORY}`/`basename ${csv_filename}`
}

function send_slack_notification {
    # Posts a notification to a Slack channel with a message about the input CSV ($1), the ingest Fester base URL ($2),
    # and the output CSV (stdin), and then outputs the message
    read csv_filename &&
    message="Input CSV $1 was updated successfully, and after Festerizing with $2 is now available at ${csv_filename}."
    curl -s -X POST -H 'Content-type: application/json' --data '{"text":${message}}' ${AVPTDP_SLACK_WEBHOOK_URL}
    echo ${message}
}

function get_ingest_fester_base_url {
    # Outputs the base URL of the ingest Fester instance associated with the provided alias
    case $1 in
        prod)
            echo "https://ingest.iiif.library.ucla.edu"
            ;;
        test)
            echo "https://test-iiif.library.ucla.edu"
            ;;
        *)
            echo "http://localhost:8888"
            ;;
    esac
}

function strip_trailing_slash {
    # Outputs the provided path with any trailing slash removed
    sed -e "s/\/$//" <<< $1
}

# Check if the required env vars are set
if [ -z "${AVPTDP_INPUT_DIRECTORY}" ]
then
    echo "The env var AVPTDP_INPUT_DIRECTORY must be set."
    exit 1
elif [ -z "${AVPTDP_FESTERIZE_OUTPUT_DIRECTORY}" ]
then
    echo "The env var AVPTDP_FESTERIZE_OUTPUT_DIRECTORY must be set."
    exit 1
elif [ -z "${AVPTDP_METAGETTER_MEDIA_DIRECTORY}" ]
then
    echo "The env var AVPTDP_METAGETTER_MEDIA_DIRECTORY must be set."
    exit 1
elif [ -z "${AVPTDP_METAGETTER_OUTPUT_DIRECTORY}" ]
then
    echo "The env var AVPTDP_METAGETTER_OUTPUT_DIRECTORY must be set."
    exit 1
elif [ -z "${AVPTDP_SLACK_WEBHOOK_URL}" ]
then
    echo "The env var AVPTDP_SLACK_WEBHOOK_URL must be set."
    exit 1
fi

ingest_fester_base_url=`get_ingest_fester_base_url $1`
>&2 echo "Using Fester instance at ${ingest_fester_base_url} for ingest."

# Get a more informative return status from our pipeline in the main loop
set -o pipefail

inotifywait -mr \
    --timefmt '%d/%m/%y %H:%M' --format '%T %w %f' \
    -e close_write \
    ${AVPTDP_INPUT_DIRECTORY} |
while read -r date time dir file; do
    # Only process files with a ".out" filename extension
    case ${file} in
        *.out)
            abs_path=${dir}${file}

            get_av_metadata ${abs_path} |
            change_filename_extension |
            festerize_ ${ingest_fester_base_url} |
            send_slack_notification ${abs_path} ${ingest_fester_base_url}
            ;;
        *)
            ;;
    esac
done
