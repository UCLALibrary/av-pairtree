#!/bin/bash

function get_av_metadata {
    # Runs the CSV at the path provided via $1 through services-metagetter and outputs the path of the result CSV
    &> /var/log/av-pairtree/metagetter.log \
    java -jar "${AVPTDP_METAGETTER_JAR_PATH}" \
        "$1" "${AVPTDP_METAGETTER_MEDIA_DIRECTORY}" $(which ffprobe) "${AVPTDP_METAGETTER_OUTPUT_DIRECTORY}" &&
        echo $(strip_trailing_slash "${AVPTDP_METAGETTER_OUTPUT_DIRECTORY}")/$(basename "$1")
}

function change_filename_extension {
    # Change filename ext of the provided path (via stdin) from .out to .csv, since festerize only looks at .csv files
    if ! read -r filename_dot_out
    then
        >&2 echo "change_filename_extension: no input filename received"
        return 1
    fi

    filename_dot_csv="${filename_dot_out%.out}.csv"

    if mv -- "${filename_dot_out}" "${filename_dot_csv}"
    then
        echo "${filename_dot_csv}"
    else
        >&2 echo "change_filename_extension: could not rename ${filename_dot_out} to ${filename_dot_csv}"
        return 1
    fi
}

function festerize_ {
    source /opt/av-pairtree/src/main/scripts/.env

    if ! read -r csv_filename || [[ -z "${csv_filename}" ]]
    then
        echo "Festerize was not run: no CSV filename received from prior pipeline step" \
            >> /var/log/av-pairtree/festerize.log
        return 1
    fi

    if festerize --strict-mode --iiif-api-version 3 \
        --server "$1" \
        --out "${AVPTDP_FESTERIZE_OUTPUT_DIRECTORY}" \
        "${csv_filename}" <<< "y" \
        &>> /var/log/av-pairtree/festerize.log
    then
        output_csv="$(strip_trailing_slash "${AVPTDP_FESTERIZE_OUTPUT_DIRECTORY}")/$(basename "${csv_filename}")"

        if [[ -f "${output_csv}" ]]
        then
            echo "${output_csv}"
        else
            echo "Festerize returned success, but expected output was not found: ${output_csv}" \
                >> /var/log/av-pairtree/festerize.log
        fi
    else
        exit_code=$?
        echo "Festerize failed for ${csv_filename}; exit code: ${exit_code}" \
            >> /var/log/av-pairtree/festerize.log
    fi
}

function send_slack_notification {
    # Posts a notification to a Slack channel with a message about the input CSV ($1), the ingest Fester base URL ($2),
    # and the output CSV (stdin), and then outputs the message
    read -r csv_filename
    if [[ -n $csv_filename ]]
    then
        message="Input CSV \`$1\` was updated successfully, and after Festerizing with $2 is now available at \`${csv_filename}\`."
    else
        message="Input CSV \`$1\` could not be processed. See server logs for details."
    fi
    curl -s -X POST -H 'Content-type: application/json' --data "{\"text\":\"${message}\"}" "${AVPTDP_SLACK_WEBHOOK_URL}"
    echo "${message}"
}

function get_ingest_fester_base_url {
    # Outputs the base URL of the ingest Fester instance associated with the provided alias
    case "$1" in
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
    sed -e "s/\/$//" <<< "$1"
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
elif [ -z "${AVPTDP_METAGETTER_JAR_PATH}" ]
then
    echo "The env var AVPTDP_METAGETTER_JAR_PATH must be set."
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

ingest_fester_base_url=$(get_ingest_fester_base_url "$1")
>&2 echo "Using Fester instance at ${ingest_fester_base_url} for ingest."

# Get a more informative return status from our pipeline in the main loop
set -o pipefail

inotifywait -mr \
    --timefmt '%d/%m/%y %H:%M' --format '%T %w %f' \
    -e close_write \
    "${AVPTDP_INPUT_DIRECTORY}" |
while read -r date time dir file; do
    # Only process files with a ".out" filename extension
    case "${file}" in
        *.out)
            abs_path="${dir}${file}"
            echo "Script triggered to further process: ${abs_path}" \
                >> /var/log/av-pairtree/debug.log

            get_av_metadata "${abs_path}" |
            change_filename_extension |
            festerize_ "${ingest_fester_base_url}" |
            send_slack_notification "${abs_path}" "${ingest_fester_base_url}"
            ;;
        *)
            ;;
    esac
done
