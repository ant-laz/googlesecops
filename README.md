## Pipeline overview

Solace >> Dataflow >> Google SecOps (Chronicle) API

## Source - Solace

https://solace.com/ is the source for this pipeline. 

Here is an example message the pipeline will be processing.

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
    "movieReview": "positive",
    "url": "https://www.rottentomatoes.com/m/raiders_of_the_lost_ark"
}
```

## Sink - Google Security Operations Chronicle API

The Google Security Operations REST API (aka the Chronicle API) is the sink.

https://cloud.google.com/chronicle/docs/reference/rest

Specifically messages from Solace will be sent to the logs import [method](https://cloud.google.com/chronicle/docs/reference/rest/v1alpha/projects.locations.instances.logTypes.logs/import)


## Setup development environment

### install java

```shell
# Install sdkman.
curl -s "https://get.sdkman.io" | bash

# Make sure you have Java 17 installed.
sdk install java 17.0.5-tem
```

## Environment variables

See here for valid dataflow locations
https://cloud.google.com/dataflow/docs/resources/locations

```sh
export GCP_PROJECT_ID=${MY_GCP_PROJECT_ID}
export GCP_PROJECT_NUM=${MY_GCP_PROJECT_NUM}
export CURRENT_USER=${MY_CURRENT_USER}
export GCP_BUCKET_REGION="${MY_GCP_BUCKET_REGION}"
export GCP_DATAFLOW_REGION="${MY_GCP_DATAFLOW_REGION}"
export PIPELINE_NAME="${MY_PIPELINE_NAME}"
export GCS_BUCKET=gs://${GCP_PROJECT_ID}-${MY_PIPELINE_NAME}
export GCS_BUCKET_TMP=${GCS_BUCKET}/tmp/
export SERVICE_ACCT=${MY_SERVICE_ACCT}
export SERVICE_ACCT_FULL="${SERVICE_ACCT}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
export IAM_CUSTOM_ROLE_ID=${MY_IAM_CUSTOM_ROLE_ID}
export SEMP_HOSTNAME=${MY_SEMP_HOSTNAME}
export JCSMP_HOSTNAME=${MY_JCSMP_HOSTNAME}
export SOLACE_USERNAME=${MY_SOLACE_USERNAME}
export SOLACE_PASSWORD=${MY_SOLACE_PASSWORD}
export VPN_NAME=${MY_VPN_NAME}
export SOLACE_QUEUE_NAME=${MY_SOLACE_QUEUE_NAME}
export GSECOPS_BYOP_GCP_PROJECT=${MY_GSECOPS_BYOP_GCP_PROJECT}
export GSECOPS_LOCATION=${MY_GSECOPS_LOCATION}
export GSECOPS_CUSTOMER_ID=${MY_GSECOPS_CUSTOMER_ID}
export GSECOPS_FORWARDER_ID=${MY_GSECOPS_FORWARDER_ID}
export GSECOPS_LOG_TYPE=${MY_GSECOPS_LOG_TYPE}
```

## Local development with Beam runner

Create local authentication credentials for your user account.

```shell
gcloud auth application-default login
```

Run the pipeline using the Beam runner.

```shell
gradle run --args="\
--serviceAccount=${SERVICE_ACCT_FULL} \
--project=${GCP_PROJECT_ID} \
--region=${GCP_DATAFLOW_REGION} \
--tempLocation=${GCS_BUCKET_TMP} \
--sempHostname=${SEMP_HOSTNAME} \
--jcsmpHostname=${JCSMP_HOSTNAME} \
--username=${SOLACE_USERNAME} \
--password=${SOLACE_PASSWORD} \
--vpnName=${VPN_NAME} \
--queueName=${SOLACE_QUEUE_NAME} \
--secOpsProject=${GSECOPS_BYOP_GCP_PROJECT} \
--secOpsLocation=${GSECOPS_LOCATION} \
--secOpsCustomerID=${GSECOPS_CUSTOMER_ID} \
--secOpsForwarderID=${GSECOPS_FORWARDER_ID} \
--secOpsLogType=${GSECOPS_LOG_TYPE}"
```



## Deploying to Dataflow

### Documentation

https://cloud.google.com/dataflow/docs/quickstarts/create-pipeline-java

### create a Google Cloud environment

1. environmental variables
2. enable APIs at the project level
3. create a service account to run the pipeline
4. create a Google Cloud Storage bucket



#### 2. enable APIs at the project level
```shell
gcloud services enable dataflow compute_component logging storage_component storage_api cloudresourcemanager.googleapis.com
```

#### 3. create a service account to run the pipeline

Create a user-managed service account to act as the Dataflow worker service account.

https://cloud.google.com/dataflow/docs/concepts/security-and-permissions#permissions

```shell
gcloud iam service-accounts create ${SERVICE_ACCT} \
    --description="service acct for requests to chronicle API" \
    --display-name="${SERVICE_ACCT}"
```

Allow yourself the permission to impersonate the new service account.
https://cloud.google.com/docs/authentication/rest#impersonated-sa

```shell
gcloud iam service-accounts add-iam-policy-binding \
    ${SERVICE_ACCT_FULL} \
    --member="user:${CURRENT_USER}" \
    --role="roles/iam.serviceAccountTokenCreator"
```

Following the principle of least privilege, create a custom IAM role with the
minimum permissions needed for the service account to carry out API operations.
Here the custom role simply has the ```chronicle.feeds.get``` permission.


```shell
gcloud iam roles create ${IAM_CUSTOM_ROLE_ID} \
  --project=${PROJECT_ID} \
  --title="${IAM_CUSTOM_ROLE_ID}" \
  --description="${IAM_CUSTOM_ROLE_ID}" \
  --permissions="chronicle.logs.import" \
  --stage=GA
```

```shell
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
  --member="serviceAccount:${SERVICE_ACCT_FULL}" \
  --role="projects/${PROJECT_ID}/roles/${IAM_CUSTOM_ROLE_ID}"
```

Grant the service account the roles to execute a dataflow pipeline 

https://cloud.google.com/dataflow/docs/concepts/security-and-permissions#worker-service-account

And "Ensure that your user-managed service account has read and write access 
to the staging and temporary locations specified in the Dataflow job." [ref](https://cloud.google.com/dataflow/docs/concepts/security-and-permissions#user-managed)

```shell
gcloud projects add-iam-policy-binding ${GCP_PROJECT_ID} \
--member="serviceAccount:${SERVICE_ACCT_FULL}" \
--role="roles/dataflow.admin"

gcloud projects add-iam-policy-binding ${GCP_PROJECT_ID} \
--member="serviceAccount:${SERVICE_ACCT_FULL}" \
--role="roles/dataflow.worker"
  
gcloud projects add-iam-policy-binding ${GCP_PROJECT_ID} \
--member="serviceAccount:${SERVICE_ACCT_FULL}" \
--role="roles/storage.objectAdmin"
```

#### 4. create a Google Cloud Storage bucket for temporary pipepline files

create a bucket
```shell
gcloud storage buckets create ${GCS_BUCKET} \
  --project=${GCP_PROJECT_ID} \
  --location=${GCP_BUCKET_REGION} \
  --uniform-bucket-level-access \
  --default-storage-class STANDARD
```

### launch the pipeline on Dataflow

```shell
gradle run --args="\
--runner='DataflowRunner' \
--serviceAccount=${SERVICE_ACCT_FULL} \
--project=${GCP_PROJECT_ID} \
--region=${GCP_DATAFLOW_REGION} \
--tempLocation=${GCS_BUCKET_TMP} \
--sempHostname=${SEMP_HOSTNAME} \
--jcsmpHostname=${JCSMP_HOSTNAME} \
--username=${SOLACE_USERNAME} \
--password=${SOLACE_PASSWORD} \
--vpnName=${VPN_NAME} \
--queueName=${SOLACE_QUEUE_NAME} \
--secOpsProject=${GSECOPS_BYOP_GCP_PROJECT} \
--secOpsLocation=${GSECOPS_LOCATION} \
--secOpsCustomerID=${GSECOPS_CUSTOMER_ID} \
--secOpsForwarderID=${GSECOPS_FORWARDER_ID} \
--secOpsLogType=${GSECOPS_LOG_TYPE}"
```


