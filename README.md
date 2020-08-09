# cloudflare-logs-forwarder

[![Actions Status](https://github.com/rieske/cloudflare-logs-forwarder/workflows/build/badge.svg)](https://github.com/rieske/cloudflare-logs-forwarder/actions)
[![Maintainability](https://api.codeclimate.com/v1/badges/e4b0e3c1814a38b4408f/maintainability)](https://codeclimate.com/github/rieske/cloudflare-logs-forwarder/maintainability)
[![Test Coverage](https://api.codeclimate.com/v1/badges/e4b0e3c1814a38b4408f/test_coverage)](https://codeclimate.com/github/rieske/cloudflare-logs-forwarder/test_coverage)

This project contains an AWS Lambda function that:

 - listens to S3 events with uploaded Cloudflare logs using Cloudflare's [logpush](https://developers.cloudflare.com/logs/logpush) service
 - compacts the json logs by cherry-picking fields of interest and producing space-delimited log lines 
(modify [CompactingLogTransformer](lambda/src/main/java/lt/rieske/logs/forwarder/CompactingLogTransformer.java)
and 
[CompactingLogTransformerTest](lambda/src/test/java/lt/rieske/logs/forwarder/CompactingLogTransformerTest.java) 
to adjust the data extraction to your needs)
 - forwards the compacted log lines in batches via HTTP to an arbitrary log aggregator 
(replace [HttpLogConsumer](lambda/src/main/java/lt/rieske/logs/forwarder/HttpLogConsumer.java)
implementation with a client of choice for your aggregator)
 
## Context

Cloudflare can be configured to [push the logs to an S3 bucket](https://developers.cloudflare.com/logs/logpush).
It does so every 5 minutes by uploading a gzipped log file where each line is a log entry in [json](https://developers.cloudflare.com/logs/log-fields) format.
Depending on the amount of traffic that goes via Cloudflare, the logs can be quite massive.
While storing them in S3 or S3 Glacier can be cheap, companies often want to extract some valuable data from those logs, 
build some analytics, correlate them with logs from other services, etc.
Both analytics and log centralization/aggregation is usually provided by [third party services](https://developers.cloudflare.com/logs/analytics-integrations)
that often charge for the amount of data ingested.
Even if the log aggregation is a self-hosted Elastic stack, the large amounts of data need to be stored somewhere.

This project demonstrates how the size of logs ingested by an aggregator can be greatly reduced 
by selecting only the data fields of interest 
and by stripping the json field names that add a lot of overhead given large amounts of log lines.
This can reduce the amount of ingested data by a factor of 10 and more depending on the data fields needed.

An overhead is that the aggregator needs to know how to parse the new format. 
If Elastic stack is used, this can be done by using a simple [GROK](https://www.elastic.co/guide/en/elasticsearch/reference/current/grok-processor.html#grok-basics) filter.
Proprietary services can also be configured to parse the custom format of incoming logs.

### Example

Let's take a Cloudflare log line with randomly generated fake data (line breaks and indentation added for readability):

```json
{
    "CacheCacheStatus":"miss",
    "CacheResponseBytes":40005,
    "CacheResponseStatus":401,
    "CacheTieredFill":false,
    "ClientASN":1010,
    "ClientCountry":"YE",
    "ClientDeviceType":"desktop",
    "ClientIP":"44.234.108.208",
    "ClientIPClass":"unknown",
    "ClientRequestBytes":8270,
    "ClientRequestHost":"netlog.com",
    "ClientRequestMethod":"PATCH",
    "ClientRequestPath":"/odio/condimentum/id/luctus/nec/molestie.js",
    "ClientRequestProtocol":"HTTP/1.1",
    "ClientRequestReferer":null,
    "ClientRequestURI":"/lacinia/aenean/sit/amet/justo/morbi/ut.js",
    "ClientRequestUserAgent":"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.11 (KHTML, like Gecko) Ubuntu/11.10 Chromium/17.0.963.65 Chrome/17.0.963.65 Safari/535.11",
    "ClientSSLCipher":"911100886-5",
    "ClientSSLProtocol":"TLSv1.2",
    "ClientSrcPort":1173,
    "ClientXRequestedWith":null,
    "EdgeColoCode":"AMS",
    "EdgeColoID":30,
    "EdgeEndTimestamp":"1577429225000",
    "EdgePathingOp":"wl",
    "EdgePathingSrc":"macro",
    "EdgePathingStatus":"nr",
    "EdgeRateLimitAction":null,
    "EdgeRateLimitID":0,
    "EdgeRequestHost":"toplist.cz",
    "EdgeResponseBytes":49926,
    "EdgeResponseCompressionRatio":1.67,
    "EdgeResponseContentType":"image/png",
    "EdgeResponseStatus":404,
    "EdgeServerIP":"245.52.27.185",
    "EdgeStartTimestamp":"1572164553000",
    "FirewallMatchesActions":"simulate",
    "FirewallMatchesRuleIDs":"927063863-4",
    "FirewallMatchesSources":null,
    "OriginIP":"128.38.86.179",
    "OriginResponseBytes":19982,
    "OriginResponseHTTPExpires":"2020-01-15T02:36:44Z",
    "OriginResponseHTTPLastModified":"2020-05-21T15:18:29Z",
    "OriginResponseStatus":400,
    "OriginResponseTime":"1568377562000",
    "OriginSSLProtocol":"TLSv1.2",
    "ParentRayID":"730180787-2",
    "RayID":"323938618-6",
    "SecurityLevel":"eoff",
    "WAFAction":"unknown",
    "WAFFlags":1,
    "WAFMatchedVar":null,
    "WAFProfile":"unknown",
    "WAFRuleID":null,
    "WAFRuleMessage":null,
    "WorkerCPUTime":0,
    "WorkerStatus":"unknown",
    "WorkerSubrequest":true,
    "WorkerSubrequestCount":0,
    "ZoneID":289326123
}
```

As you can see, the payload includes quite a bit of data, totalling `1862` bytes. 
That's a single log line for a single serviced request.
Let's say for some arbitrary analytics purposes we are only interested in the following fields:

```json
{
    "CacheCacheStatus":"miss",
    "ClientCountry":"YE",
    "ClientIP":"44.234.108.208",
    "ClientRequestHost":"netlog.com",
    "ClientRequestMethod":"PATCH",
    "ClientRequestURI":"/lacinia/aenean/sit/amet/justo/morbi/ut.js",
    "ClientRequestUserAgent":"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.11 (KHTML, like Gecko) Ubuntu/11.10 Chromium/17.0.963.65 Chrome/17.0.963.65 Safari/535.11",
    "EdgeEndTimestamp":"1577429225000",
    "EdgeResponseBytes":49926,
    "EdgeResponseStatus":404,
    "EdgeStartTimestamp":"1572164553000",
    "RayID":"323938618-6"
}
```

If we simply remove the fields that are of no interest before sending the log to an aggregator, we already reduce 
the payload size of this single request to `514` bytes.

As a next step, we can remove the json field names and maintain the mapping of values to fields at the log aggregator side.
The aggregator would then be configured to map space delimited values to:

```
ClientRequestMethod ClientRequestHost ClientRequestURI ClientIP ClientCountry EdgeResponseStatus EdgeResponseBytes CacheCahceStatus RayID EdgeStartTimestamp EdgeEndTimestamp ClientRequestUserAgent
```

while the payload that it ingests for each log line would look like:

```
PATCH netlog.com /lacinia/aenean/sit/amet/justo/morbi/ut.js 44.234.108.208 YE 404 49926 miss 323938618-6 1572164553000 1577429225000 Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.11 (KHTML, like Gecko) Ubuntu/11.10 Chromium/17.0.963.65 Chrome/17.0.963.65 Safari/535.11
```

That's `271` bytes of data instead of original `1862` bytes.
Note that the original data is not lost - it is still stored in the S3 bucket and is available in case there arises
a need to reingest a different subset of the data or even chew it full.


## Project structure

This project contains source code and supporting files for a serverless application that you can deploy with the [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html).

- lambda - Code and tests for the application's Lambda function.
- template.yaml - A SAM template that defines the application's AWS resources.

The application itself is written in Java11 and built using Gradle.
The deployment uses several AWS resources: Cloudformation, Lambda and an S3 bucket. 
See [Deployment](#Deployment) section below for required permissions.

## Build

This lambda function is a regular Java application built using Gradle. 
The only prerequisite to build and test it is Java11.

To build and test the lambda function, run:

```bash
./gradlew build
```

## Deployment

The Serverless Application Model Command Line Interface (SAM CLI) is an extension of the AWS CLI that adds 
functionality for building and deploying Lambda applications. 

To deploy the lambda function, [download and install the SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html).
Make sure the AWS CLI user that will deploy the application has the following permissions 
(you can use the policy template below to create a custom policy in IAM console and attach it to an unprivileged CLI user):
```yaml
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SamCliCloudformation",
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateChangeSet",
        "cloudformation:DescribeChangeSet",
        "cloudformation:ExecuteChangeSet",
        "cloudformation:DescribeStacks",
        "cloudformation:DescribeStackEvents",
        "cloudformation:GetTemplateSummary"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "SamCliS3",
      "Effect": "Allow",
      "Action": [
        "s3:CreateBucket",
        "s3:PutBucketTagging",
        "s3:PutBucketVersioning",
        "s3:PutBucketPolicy",
        "s3:GetBucketPolicy",
        "s3:PutObject",
        "s3:GetObject",
        "s3:PutLifecycleConfiguration",
        "s3:PutBucketNotification"
      ],
      "Resource": [
        "arn:aws:s3:::*"
      ]
    },
    {
      "Sid": "SamCliLambda",
      "Effect": "Allow",
      "Action": [
        "lambda:CreateFunction",
        "lambda:GetFunction",
        "lambda:DeleteFunction",
        "lambda:AddPermission",
        "lambda:RemovePermission",
        "lambda:GetFunctionConfiguration",
        "lambda:UpdateFunctionConfiguration",
        "lambda:UpdateFunctionCode",
        "lambda:ListTags",
        "lambda:TagResource",
        "lambda:UntagResource"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "SamCliIAM",
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "SamCliLogs",
      "Effect": "Allow",
      "Action": [
        "cloudformation:DescribeStackResource",
        "logs:FilterLogEvents"
      ],
      "Resource": [
        "*"
      ]
    }
  ]
}
```

To build and deploy the serverless application, run the following:

```bash
sam build
sam deploy --guided
```

The first command will build the source of your application using Gradle and
create a deployment package in the `.aws-sam/build` folder.
The second command will package and deploy your application to AWS, with a series of prompts:

* **Stack Name**: The name of the stack to deploy to CloudFormation. This should be unique to your account and region, default will be `cloudflare-logs-forwarder`.
* **AWS Region**: The AWS region you want to deploy your app to.
* **LambdaRoleArn**: ARN of the role with attached AWSLambdaExecute policy
* **CloudflareLogPushUserArn**: ARN of the Cloudflare user that will be pushing logs to this bucket. You will get this ARN when configuring the logpush in Cloudflare.
* **LogForwarderHttpEndpoint**: HTTP endpoint where the logs will be forwarded to - this is just an example - modify the source of the function and the passed variable per your needs.
* **LogForwarderCredentials**: Basic Auth token for the HTTP ingest HTTP endpoint - this is just an example - modify the source of the function and the passed variable per your needs.
* **Confirm changes before deploy**: If set to yes, any change sets will be shown to you before execution for manual review. If set to no, the AWS SAM CLI will automatically deploy application changes.
* **Allow SAM CLI IAM role creation**: This template is configured to use an external role, passed by **LambdaRoleArn** parameter above so that the function can be deployed by an unprivileged user. Answer `n` here.
* **Save arguments to samconfig.toml**: If set to yes, your choices will be saved to the `samconfig.toml` file, so that next time you can just re-run `sam build && sam deploy` without parameters to deploy updates to the lambda function.

Alternatively, instead of guided deployment, you can supply all the required arguments via the command line:

```bash
sam deploy \
    --region=<region> \
    --s3-bucket=<s3-bucket-where-lambda-sources-will-be-uploaded> \
    --parameter-overrides="LambdaRoleArn=<lambda-role> CloudflareLogPushUserArn=<cloudflare-logpush-user-arn> LogForwarderHttpEndpoint=<your-endpoint> LogForwarderCredentials=<your-credentials>"
```

## Logs

The deployed lambda function will send its logs to Cloudwatch.
You can tail them from the command line using SAM CLI:

```bash
sam logs -n CloudflareLogsForwarderFunction --stack-name cloudflare-logs-forwarder --tail
```

Note that the above command will only work after the lambda has produced some logs, that is after it has executed at least once.
You can manually drop one of the test log archives from `lambda/src/test/resources/` to the logs S3 bucket
to trigger the lambda if you don't have Cloudflare logpush connected and want to try this out.

You can find more information and examples about filtering Lambda function logs in the [SAM CLI Documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-logging.html).
