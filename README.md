## Pipeline overview

source = Pub/Sub

sink = Chronicle

## Sink - Google SecOps

### Import 1 - Google Cloud Storage

Create the Data Feed with type google cloud storage
Get Google SecOps to generate a service account
Add the service account to an environmental variable for later

### Import 2 - Chronicle API

Create a service account in the BYOP linked project
Grant service account permissions to write to the api
Download the service account key
store every in environmental varibles & secret handlers

## Source - Solace

Example message from Solace

```shell
{
    "message": "{{alpha(integer(5000,15000)}}",
    "critical_fields": {"timestamp_iso_format": "2023-10-03T10:10:04+00:00"},
    "metadata": {
        "uuaa": "uuaa8",
        "datachannel": "channel8",
        "write_bq": false,
        "write_gcs": true
},
    "review": "positive",
    "url": "https://www.rottentomatoes.com/m/raiders_of_the_lost_ark"
}
```

## Setup development environment

### install java

```shell
# Install sdkman.
curl -s "https://get.sdkman.io" | bash

# Make sure you have Java 17 installed.
sdk install java 17.0.5-tem
```

## Local development with beam runner

To run passing command line arguments.

```shell
./gradlew run --args="\
--sempHostname=${} \
--jcsmpHostname=${} \
--username=${} \
--password=${} \
--vpnName=${} \
--queueName=${}"
```

## Deploying to Dataflow

### Documentation

https://cloud.google.com/dataflow/docs/quickstarts/create-pipeline-java

### create a Google Cloud environment

environment variables

See here for valid dataflow locations
https://cloud.google.com/dataflow/docs/resources/locations

```sh
export GCP_PROJECT_ID=$(gcloud config list core/project --format="value(core.project)")
export GCP_PROJECT_NUM=$(gcloud projects describe $GCP_PROJECT_ID --format="value(projectNumber)")
export CURRENT_USER=$(gcloud config list account --format "value(core.account)")
export GCP_BUCKET_REGION="US"
export GCP_DATAFLOW_REGION="us-east4"
export PROJECT_NAME="pubsubchronicle"
export GCS_BUCKET=gs://${GCP_PROJECT_ID}-${PROJECT_NAME}
export GCS_BUCKET_TMP=${GCS_BUCKET}/tmp/
export GCS_BUCKET_INPUT=${GCS_BUCKET}/input
export GCS_BUCKET_OUTPUT=${GCS_BUCKET}/output
export GCS_BUCKET_PUBSUB_INPUT=${GCS_BUCKET}/pubsubinput
export PUB_SUB_TOPIC=projects/${GCP_PROJECT_ID}/topics/${PROJECT_NAME}
export PUB_SUB_SUBSCRIPTION=${PROJECT_NAME}-sub
export PUB_SUB_SUBSCRIPTION_DEBUG=${PROJECT_NAME}-sub-debug
export SUBSCRIPTION_ID=projects/${GCP_PROJECT_ID}/subscriptions/${PUB_SUB_SUBSCRIPTION}
```

enable apis
```shell
gcloud services enable dataflow compute_component logging storage_component storage_api bigquery pubsub datastore.googleapis.com cloudresourcemanager.googleapis.com
```

create a bucket
```shell
gcloud storage buckets create ${GCS_BUCKET} \
  --project=${GCP_PROJECT_ID} \
  --location=${GCP_BUCKET_REGION} \
  --uniform-bucket-level-access

gcloud storage buckets add-iam-policy-binding ${GCS_BUCKET} \
--member=allUsers \
--role=roles/storage.objectViewer

```

```shell
gradle run --args="\
--pubSubSubscription=${SUBSCRIPTION_ID} \
--runner='DataflowRunner' \
--project=${GCP_PROJECT_ID} \
--region=${GCP_DATAFLOW_REGION} \
--experiments=enable_data_sampling \
--tempLocation=${GCS_BUCKET_TMP}"
```

