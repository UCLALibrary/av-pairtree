# A/V Pairtree Scripts

Various scripts for use with the A/V Pairtree application.

## avpt_data_pipeline.sh

This script constructs a data processing pipeline consisting of A/V Pairtree, Metagetter, and Festerize (in that order), in which the output CSV files of each component application are passed to the next one for further processing.

### Installation

Dependencies:
- GNU bash (written for version 4.2.46(2)-release (x86_64-redhat-linux-gnu))
- GNU coreutils
- curl
- ffmpeg
- [inotifywait](https://github.com/inotify-tools/inotify-tools)
- [UCLALibrary/services-metagetter](https://github.com/UCLALibrary/services-metagetter)
- [UCLALibrary/festerize](https://github.com/UCLALibrary/festerize)

### Usage

The following environment variables must be set:

Environment variable|Description
---|---
AVPTDP_INPUT_DIRECTORY|directory where A/V Pairtree puts .out files; this is the input directory for the pipeline (and thus, for Metagetter)
AVPTDP_FESTERIZE_OUTPUT_DIRECTORY|directory where Festerize puts .csv files
AVPTDP_METAGETTER_MEDIA_DIRECTORY|directory where Metagetter will search for A/V media files
AVPTDP_METAGETTER_OUTPUT_DIRECTORY|directory where Metagetter puts .out files (which are then renamed as .csv); this is the input directory for Festerize
AVPTDP_SLACK_WEBHOOK_URL|URL of the webhook for posting to Slack

The script takes a single optional positional argument: an alias for the ingest Fester instance to Festerize the data with. If omitted, or if an unknown alias is used, the script will point Festerize at http://localhost:8888.

Known aliases:

Argument|Description
---|---
prod|https://ingest.iiif.library.ucla.edu
test|https://test-iiif.library.ucla.edu

For example:

```bash
#!/bin/bash

export AVPTDP_INPUT_DIRECTORY="avpt_output/"
export AVPTDP_FESTERIZE_OUTPUT_DIRECTORY="festerize_output/"
export AVPTDP_METAGETTER_MEDIA_DIRECTORY="metagetter_media/"
export AVPTDP_METAGETTER_OUTPUT_DIRECTORY="metagetter_output/"
export AVPTDP_SLACK_WEBHOOK_URL="https://hooks.slack.com/services/0123456789"

./avpt_data_pipeline.sh prod
```
