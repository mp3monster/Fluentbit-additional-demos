package com.oracle.flb.chatops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SlackActions {

  private static final String BEARER_SLACK_HDR = "Bearer ";

  private static final String METADATA_SLACK_ATTRIBUTE = "include_all_metadata";

  private static final String LIMIT_SLACK_ATTRIBUTE = "limit";

  private static final String CHANNEL_SLACK_ATTRIBUTE = "channel";

  private static final String SLACKTIMELIMIT = "SLACK-TIME-LIMIT";

  private static final String SLACKMSGLIMIT = "SLACK-MSG-LIMIT";

  private static final String SLACKTOKEN = "SLACK-TOKEN";

  private static final String SLACKCHANNELID = "SLACK-CHANNEL-ID";

  private static final int DEFAULTMSGLIMIT = 5;

  private static final String FIND_COMMANDS_JSONPATH = "$.messages[*].text";
  private static final String FIND_OK_JSONPATH = "$.ok";

  private static final String FLB = "FLBCmd:";
  private static final String FLBNODE = "FLBNode:";

  private static final String SLACKURL = "https://slack.com/api/conversations.history";

  // curl -d "channel=CMM13QSBB" -d "limit=3" -d "include_all_metadata=false" -H
  // "Authorization: Bearer
  // xoxb-YOUR TOKEN HERE -X POST
  // https://slack.com/api/conversations.history

  private String channelId;
  private String mySlackToken;
  private boolean myIncludeMetadata = false;
  private int myMsgCountLimit;
  private Integer myMsgTimeLimit = null;
  FLBCommunication myResults = new FLBCommunication();
  private ReadContext myJsonCtx = null;
  private String alertId = "";

  public static Map<String, String> addProperties(Map<String, String> config) {

    String channelId = config.getOrDefault(SLACKCHANNELID, null);
    if ((channelId == null) || (channelId.trim().length() == 0)) {
      channelId = System.getenv(SLACKCHANNELID);
      if ((channelId == null) || (channelId.trim().length() == 0)) {
        FLBSocialCommandResource.LOGGER.info("SLACKCHANNELID is not set");
      }
    }
    config.put(SLACKCHANNELID, channelId);

    String mySlackToken = config.getOrDefault(SLACKTOKEN, null);
    if ((mySlackToken == null) || (mySlackToken.trim().length() == 0)) {
      mySlackToken = System.getenv(SLACKTOKEN);
      if ((mySlackToken == null) || (mySlackToken.trim().length() == 0)) {

        FLBSocialCommandResource.LOGGER.info("SlackToken is not set");
      }
    }
    config.put(SLACKTOKEN, mySlackToken);

    int msgCountLimit = DEFAULTMSGLIMIT;
    try {
      msgCountLimit = Integer.parseInt(config.getOrDefault(SLACKMSGLIMIT, Integer.toString(DEFAULTMSGLIMIT)));
    } catch (NumberFormatException err) {
      FLBSocialCommandResource.LOGGER.info("Ignoring config msg limit value - couldnt process");
    }
    config.put(SLACKMSGLIMIT, Integer.toString(msgCountLimit));

    int myMsgTimeLimit = 0;
    if (config.containsKey(SLACKTIMELIMIT)) {
      try {
        myMsgTimeLimit = new Integer(config.get(SLACKTIMELIMIT));
      } catch (NumberFormatException err) {
        FLBSocialCommandResource.LOGGER.info("Ignoring config time limit value - couldnt process");
      }
    }
    config.put(SLACKTIMELIMIT, Integer.toString(myMsgTimeLimit));

    return config;
  }

  SlackActions(Map<String, String> config) {

    channelId = System.getenv(SLACKCHANNELID);

    mySlackToken = System.getenv(SLACKTOKEN);
    myMsgCountLimit = Integer.parseInt(config.getOrDefault(SLACKMSGLIMIT, Integer.toString(DEFAULTMSGLIMIT)));
    myMsgTimeLimit = new Integer(config.get(SLACKTIMELIMIT));

  }

  private MultivaluedHashMap<String, String> initialiseParams() {
    MultivaluedHashMap<String, String> entity = new MultivaluedHashMap<>();
    entity.add(CHANNEL_SLACK_ATTRIBUTE, channelId);
    entity.add(LIMIT_SLACK_ATTRIBUTE, Integer.toString(myMsgCountLimit));
    entity.add(METADATA_SLACK_ATTRIBUTE, Boolean.toString(myIncludeMetadata));

    // alternative to
    // https://api.slack.com/methods/conversations.history
    return entity;
  }

  private String extractCommand(String identifier, String candidate, String command) {
    String commandCandidate = candidate.substring(candidate.indexOf(identifier) + identifier.length()).trim();
    if (commandCandidate.length() > 0) {
      int space = commandCandidate.indexOf(" ");
      int quote = commandCandidate.indexOf("\"");
      if ((quote > 0) && (quote < space)) {
        space = quote;
      }

      if (space > 0) {
        commandCandidate = commandCandidate.substring(0, space);
      }
      commandCandidate = commandCandidate.trim();
      if (commandCandidate.length() > 0) {
        if (command != null) {
          FLBSocialCommandResource.LOGGER
              .info("extractCommand replacing " + command + " with " + commandCandidate);
        }
        command = commandCandidate;
      }
    }
    return command;
  }

  private String findInResponse(String path, String identifier, String descriptor) {
    FLBSocialCommandResource.LOGGER
        .fine("checking response with " + path + " identifier " + identifier + " for descriptor " + descriptor);

    String command = null;
    String commandCandidate = null;
    List result = myJsonCtx.read(path);
    if (!result.isEmpty()) {
      FLBSocialCommandResource.LOGGER.finer("findInResponse found json candidate for " + descriptor);

      Iterator iter = result.iterator();
      while (iter.hasNext()) {
        String test = (String) iter.next();
        if (test.contains(identifier)) {
          FLBSocialCommandResource.LOGGER.finer("findInResponse testing:" + test);
          command = extractCommand(identifier, test, command);
        }
      }
    }

    return command;

  }

  private boolean okResponse() {
    Boolean result = myJsonCtx.read(FIND_OK_JSONPATH);
    return result.booleanValue();
  }

  public FLBCommunication checkForAction() {
    Client client = null;
    try {
      client = ClientBuilder.newClient();
      WebTarget target = client.target(SLACKURL);
      Response response = target.request()
          .header(HttpHeaders.AUTHORIZATION, BEARER_SLACK_HDR + mySlackToken).post(Entity.form(initialiseParams()));

      String payload = response.readEntity(String.class);
      client.close();

      myResults.addRawEvent("raw payload ...\n" + payload);
      this.myJsonCtx = JsonPath.parse(payload);
      myResults.addRawEvent("JsonPath evaluation = " + okResponse());

      if (okResponse()) {
        myResults.setCommand(findInResponse(FIND_COMMANDS_JSONPATH, FLB, "executeCommand"));
        myResults.setFLBNode(findInResponse(FIND_COMMANDS_JSONPATH, FLBNODE, "executionNode"));

      }

    } catch (Exception error) {
      myResults.addRawEvent(
          "Slack call error for \n:" + error.toString() + alertId + "\n" + error.getStackTrace()[0].toString() + "\n");
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return myResults;
  }
}
