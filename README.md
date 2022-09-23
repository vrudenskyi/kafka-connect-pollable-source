
This connector allows Kafka Connect to receive data from different services, APIs etc

# Configuration

## PollableAPIClientSourceConnector

The Source Connector will receive data from network  and write to kafka a topic.
```properties
name=pollableConnector
tasks.max=1
connector.class=com.vrudenskyi.kafka.connect.source.PollableAPIClientSourceConnector
topic=api_data

# Set these required values
apiclient.class=com.vrudenskyi.kafka.connect.source.PollableAPIClient
poll.interval = 30000

##Client specific configs
```
### Configuration options
| Name | Description| Type| Default | Importance |Notes
|---|---|---|---|---|---|
|apiclient.class|Class implemented interface [PollableAPIClient](src/main/java/com/vrudenskyi/kafka/connect/source/PollableAPIClient.java) |class||high|
|topic | Kafka topic name | string | | high
|poll.interval|Poll frequency in millis| long | 5000 | medium
|poll.size | Target poll size. | int | 1000 | medium | api.client implementation is responsible for number of records that will be returned.
|poll.retries | Number of retries before task fails | int |3 | medium
|poll.backoff| Millis to wait before next retry| long | 30000 | medium |
|poll.cron | Poll cron expression. | string | | low | if configured `poll.interval` will define how often task will wake up for cron check.
|reset.offsets| Reset client's reading offset| boolean | false | low | value is checked on task start. if 'true' `PollableAPIClient.initialOffset` for partition will be called.

### PollableAPIClient implementations
some of  implementations available [here](https://github.com/vrudenskyi/kafka-connect-api-clients)

- Http client - poll data from an http endpoint
- Salesforce  Client - read Saleforce EventLog events and read SObject(s)
- Splunk Client - poll data from Splunk reports
- Jira Client - poll form Jira
and more...

Check each project docs for more details

### Sample configurations
#### Http Client - read from Jamf server
```properties
connector.class=com.vrudenskyi.kafka.connect.source.PollableAPIClientSourceConnector
apiclient.class=com.vrudenskyi.kafka.connect.http.JsonGetAPIClient
tasks.max=1
topic=src_jamf
poll.interval=3600000
poll.cron=0 15 * * *

## Client configs
http.serverUri=https://your.jamf.server
http.endpoint=/JSSResource/computerreports/id/0
http.auth.type=basic
http.auth.basic.user=user
http.auth.basic.password=password
json.data.pointer=/computer_reports
```
#### Salesforce - read Event log
```properties
connector.class=com.vrudenskyi.kafka.connect.source.PollableAPIClientSourceConnector
apiclient.class=com.vrudenskyi.kafka.connect.salesforce.EventLogPollableAPIClient
topic=src_sfdc_eventlog
poll.interval=3600000

## Client configs
sfdc.user=<user>
sfdc.password=<password>
sfdc.token=<token>
```

