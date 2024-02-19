# Introduction

This documentation covers the build, configuration, and execution of the ChatOps demo using Slack and Fluent Bit.

This is an MVP to illustrate the possibility and explore the security considerations and implications.

*Note text in italics in the following documentation represents functionality not yet implemented.*

### PreRequisities

To build and run the following demo, you'll need:

- Slack with admin privileges to:
  - create and configure an API token 
  - Create a channel to communicate with Fluent Bit with
- Compute platform to run:
  -  the Java/GraalVM application 
  - one or more compute nodes to run Fluent Bit on (can be containers)
  - Log simulator/event generation capability
  - CURL for testing
- For building the Slack response handler
  - Maven for compiling the code
  - Java (ideally Java21 to benefit from the virtual thread optimizations)

# Solution 

The solution operates in the following manner:

![](.\flow-diagram.png)



### Fluent Bit

Fluent Bit has two paths to handle:

1. Handling of normal events and identifying which event(s) may need to be shared via Slack for human intervention
2. Handling of the human action coming from Slack to execute a remediation operation. This comes as an HTTP event (*an enhancement to make things secure with SSL should be made available*)

The Fluent Bit resources for this all reside within the folder **../fluentbit**.

The Fluent Bit configuration currently makes use of the classic syntax and has the Lua scripting deployed alongside it. 

A test script that will mimic the Slack handler call is provided, which makes use of CURL - called **test-cmd.[bat|sh]**

### Java/GraalVM Response Handler 

This has been built with the [Helidon](https://helidon.io/) framework so that the code can be *built to a native binary using [GraalVM](https://www.graalvm.org/)* for optimal performance and footprint or just run using Java (ideally Java21). Given the nature of the functionality, it could be deployed as a serverless solution (e.g., using *[OCI Functions](https://www.oracle.com/uk/cloud/cloud-native/functions/)*), a microservice in Kubernetes (such as [*OKE*](https://www.oracle.com/uk/cloud/cloud-native/container-engine-kubernetes/)), or a simple free-standing application.

Helidon provides by default its own simple app server and has been configured to generate metrics for the endpoints it supports - **/social**

The Java code is structured such that different implementations of the Social Channel could be implemented.

##### Configuration Requirements

The app can take the following configuration information:

| Configuration Name | Description                                                  | Example / Default Value | Mandatory |
| ------------------ | ------------------------------------------------------------ | ----------------------- | --------- |
| RETRYINTERVAL      |                                                              | 60                      |           |
| RETRYCOUNT         |                                                              | 2                       |           |
| PORT               | The Port that the target Fluent Bit HTTP                     | 2020                    |           |
| SLACKCHANNELID     | This is the true channel Id, rather than the user-friendly channel name |                         | Y         |
| SLACKTOKEN         | The token that will authenticate the app with Slack          | xoxb-XXXXXXX            | Y         |
| SLACKMSGLIMIT      |                                                              |                         |           |
| SLACKTIMELIMIT     |                                                              |                         |           |



### Detecting the Response

The agent is waiting for a response directed back to the agent e.g. t is then looking specifically looking for two value pairs in the response:

- **FLB**:<script name without a post fix>
- **FLBNode**:<node address>

> **Note**: By only accepting the name of a script to execute, we prevent arbitrary scripts from being invoked. This represents a means to provide security, but the framework could easily be extended to supply a script if this was deemed acceptable.

When these values are identified in the response then an HTTP call to the node using the preconfigured port is invoked with a JSON payload in the following form:

{\"cmd\":\"commandname\"}

*Rather than the node, we could address the node via an incident ID, which the response handler could then look up, having previously recorded it with the response handler.*
