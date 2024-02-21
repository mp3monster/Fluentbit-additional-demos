
package com.oracle.flb.chatops;

import io.helidon.logging.common.HelidonMdc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import static jakarta.ws.rs.client.Entity.json;

import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/simple-greet
 *
 * The message is returned as a JSON object.
 */
@ApplicationScoped
@Path("/social")
public class FLBSocialCommandResource {
    private static final int DEFAULT_RETRY_COUNT = 2;
    private static final int DEFAULT_RETRY_INTERVAL = 60;
    private static final String FLB_DEFAULT_PORT = "2020";
    private static final String PORT = "OPS_PORT";
    private static final String RETRYINTERVAL = "OPS_RETRYINTERVAL";
    private static final String RETRYCOUNT = "OPS_RETRYCOUNT";

    static final Logger LOGGER = Logger.getLogger(FLBSocialCommandResource.class.getName());
    private String myFLBPort = FLB_DEFAULT_PORT;
    private int myRetryDelay = 60;
    private int myRetryCount;
    Map<String, String> myEnvs = System.getenv();

    /*
     * private void signalFLBNode(String node, String command) {
     * Client client = null;
     * try {
     * String messageBody = "{command='" + command + "'}";
     * String NodeAddress = node + FLBPort;
     * client = ClientBuilder.newClient();
     * WebTarget target = client.target(NodeAddress);
     * Response response = target.request()
     * .header()
     * .post(messageBody);
     * } catch (Exception err) {
     * // TODO
     * System.out.println("bugger");
     * }
     * };
     */

    private String createFLBPayload(String command) {
        // private Entity<String> createFLBPayload(String command) {

        return "{\"command\":\"" + command + "\"}\n";
        // Entity.json()
    }

    private void signalFLBNode(String node, String port, String command, String tag) {
        // WebTarget client = WebTarget.builder()
        // .baseUri(url)
        // .config(config.get("client"))
        // .build();
        // try (HttpClientResponse response =
        // client.put("/greeting").submit("JSON_NEW_GREETING")) {
        // System.out.println("PUT request executed with status: " + response.status());
        // return response.status();
        // }
        Client client = null;
        String svr = "http://" + node + ":" + port;
        LOGGER.info("Sending to FLB Node " + svr + " command " + createFLBPayload(command) + " tagged as " + tag);
        try {
            client = ClientBuilder.newClient();
            // WebTarget target =
            // client.target(svr).path(tag).request(MediaType.APPLICATION_JSON);
            // Response response = target.request().messageBody(createFLBPayload(command))
            // .post();
            client.target(svr).path(tag).request(MediaType.APPLICATION_JSON)
                    .buildPost(json(createFLBPayload(command))).invoke();
            // String payload = response.readEntity(String.class);
            // LOGGER.fine("Response to signalFLBNode =\n" + payload);

            client.close();
        } catch (Exception err) {
            LOGGER.warning(err.toString());
        } finally {
            if (client != null) {
                client.close();
            }
        }

    }

    FLBSocialCommandResource() {
        HelidonMdc.set("name", "FLBSocial");

        try {
            int aPort = Integer.parseInt(myEnvs.getOrDefault(PORT, FLB_DEFAULT_PORT));
            myFLBPort = Integer.toString(aPort);
        } catch (NumberFormatException numErr) {
            LOGGER.warning("Couldn't process port override");
        }

        try {
            myRetryCount = Integer.parseInt(myEnvs.getOrDefault(RETRYCOUNT, Integer.toString(DEFAULT_RETRY_COUNT)));
        } catch (NumberFormatException numErr) {
            LOGGER.warning("Couldn't process retry count - using default");
            myRetryCount = DEFAULT_RETRY_COUNT;
        }

        try {
            myRetryDelay = Integer
                    .parseInt(myEnvs.getOrDefault(RETRYINTERVAL, Integer.toString(DEFAULT_RETRY_INTERVAL)));
        } catch (NumberFormatException numErr) {
            LOGGER.warning("Couldn't process retry interval - using default");
            myRetryDelay = DEFAULT_RETRY_INTERVAL;
        }
    }

    /**
     * @param alertId
     * @return String
     */
    private String checkForAction(String alertId) {
        String result;
        LOGGER.info("internal check");
        try {
            SlackActions action = new SlackActions(myEnvs);
            FLBCommunication comms = action.checkForAction(alertId);
            if (comms.getFLBNode() != null) {
                LOGGER.info("WE HAVE A NODE");
            }
            result = comms.toString();
            // TODO: if no action then wait and try again
        } catch (Exception error) {
            result = "error:" + error.toString();
        }

        return result;
    }

    /**
     * @return String
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String getNoAlertId() {
        return "{getNoAlertId= " + checkForAction("") + "}";

    }

    /**
     * @return String
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String postAlertNoId(String entity) {
        return "{postAlertNoId= " + checkForAction("") + "}";

    }

    /**
     * @return String
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String putAlertNoId(String entity) {
        return "{PutNoAlertId= " + checkForAction("") + "}";

    }

    @Path("/{alertId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String postWithAlertId(@PathParam("alertId") String alertId) {

        return "{postWithAlertId= " + checkForAction(alertId) + ", alertId=\"" + alertId + "\"}";

    }

    /**
     * @return String
     */
    @Path("/testFLB")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String postTestFLB(String entity) {
        boolean testFLB = Boolean.parseBoolean(myEnvs.getOrDefault("TESTFLB", "FALSE"));
        LOGGER.info("Test FLB allowed = " + testFLB);
        if (testFLB) {

            signalFLBNode(myEnvs.getOrDefault("TESTFLB-NODE", "localhost"), myEnvs.getOrDefault("TESTFLB-PORT", "8090"),
                    myEnvs.getOrDefault("TESTFLB-COMMAND", "test"), myEnvs.getOrDefault("TESTFLB-TAG", "command"));
        }
        return "{TestFLBDisabled= \"" + !testFLB + "\"}";

    }
}
