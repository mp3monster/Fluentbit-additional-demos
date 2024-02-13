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

  private static final String SLACKTIMELIMIT = "SLACKTIMELIMIT";

  private static final String SLACKMSGLIMIT = "SLACKMSGLIMIT";

  private static final String SLACKTOKEN = "SLACKTOKEN";

  private static final String SLACKCHANNELID = "SLACKCHANNELID";

  private static final int DEFAULTMSGLIMIT = 5;

  private static final String FIND_COMMANDS_JSONPATH = "$.messages[*].text";
  private static final String FIND_OK_JSONPATH = "$.ok";

  private static final String FLB = "FLB:";
  private static final String FLBNODE = "FLBNode:";

  private static final String SLACKURL = "https://slack.com/api/conversations.history";

  // curl -d "channel=CMM13QSBB" -d "limit=3" -d "include_all_metadata=false" -H
  // "Authorization: Bearer
  // xoxb-735037803329-6179690984166-MLSoE7JpBbyIQ2i1MDkFmqQY" -X POST
  // https://slack.com/api/conversations.history

  private String channelId;
  private String mySlackToken;
  private boolean myIncludeMetadata = false;
  private int myMsgCountLimit;
  private Integer myMsgTimeLimit = null;
  FLBCommunication myResults = new FLBCommunication();
  private ReadContext myJsonCtx = null;

  SlackActions(Map<String, String> config) {

    channelId = config.getOrDefault(SLACKCHANNELID, null);
    if ((channelId == null) || (channelId.trim().length() == 0)) {
      FLBSocialCommandResource.LOGGER.info("SLACKCHANNELID is not set");
    }
    mySlackToken = config.getOrDefault(SLACKTOKEN, null);
    if ((mySlackToken == null) || (mySlackToken.trim().length() == 0)) {
      FLBSocialCommandResource.LOGGER.info("SlackToken is not set");
    }

    try {
      myMsgCountLimit = Integer.parseInt(config.getOrDefault(SLACKMSGLIMIT, Integer.toString(DEFAULTMSGLIMIT)));
    } catch (NumberFormatException err) {
      myMsgCountLimit = DEFAULTMSGLIMIT;
      FLBSocialCommandResource.LOGGER.info("Ignoring config msg limit value - couldnt process");
    }

    if (config.containsKey(SLACKTIMELIMIT)) {
      myMsgTimeLimit = new Integer(config.get(SLACKTIMELIMIT));
    }

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

  private String findInResponse(String path, String identifier, String descriptor) {
    String command = null;
    String commandCandidate = null;
    List result = myJsonCtx.read(path);
    if (!result.isEmpty()) {
      Iterator iter = result.iterator();
      while (iter.hasNext()) {
        Object test = iter.next();
        if ((test instanceof String) && (((String) test).startsWith(identifier))) {
          commandCandidate = ((String) test).substring(identifier.length()).trim();
          if (commandCandidate.length() > 0) {
            if (command == null) {
              command = commandCandidate;
            } else {
              myResults.addRawEvent("Ignoring " + descriptor + "= " + commandCandidate);
            }
          }
        }
      }
    }
    return command;
  }

  private boolean okResponse() {
    Boolean result = myJsonCtx.read(FIND_OK_JSONPATH);
    return result.booleanValue();
  }

  public FLBCommunication checkForAction(String alertId) {
    Client client = null;
    try {
      client = ClientBuilder.newClient();
      WebTarget target = client.target(SLACKURL);
      Response response = target.request()
          .header(HttpHeaders.AUTHORIZATION, BEARER_SLACK_HDR + mySlackToken)
          .post(Entity.form(initialiseParams()));

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
