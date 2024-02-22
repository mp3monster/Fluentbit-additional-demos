
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
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
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
    private static final String TESTFLB = "TESTFLB";
    private static final String TESTFLB_TAG = "TESTFLB-TAG";
    private static final String TESTFLB_PORT = "TESTFLB-PORT";
    private static final String TESTFLB_COMMAND = "TESTFLB-COMMAND";
    private static final String TESTFLB_NODE = "TESTFLB-NODE";
    private static final int DEFAULT_RETRY_COUNT = 2;
    private static final int DEFAULT_RETRY_INTERVAL = 60;
    private static final String FLB_DEFAULT_PORT = "2020";
    private static final String PORT = "OPS_PORT";
    private static final String RETRYINTERVAL = "OPS_RETRYINTERVAL";
    private static final String RETRYCOUNT = "OPS_RETRYCOUNT";

    static final Logger LOGGER = Logger.getLogger(FLBSocialCommandResource.class.getName());
    private String myFLBPort = FLB_DEFAULT_PORT;
    private int myRetryDelay = 60;
    private int myRetryCount = 0;
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH-mm-ss");
    private Map<String, String> myEnvs = new HashMap<>();

    private static String createFLBPayload(String command) {
        return "{\"command\":\"" + command + "\"}\n";
    }

    private static void signalFLBNode(String node, String command, String tag) {

        Client client = null;
        String svr = "http://" + node;
        LOGGER.info("Sending to FLB Node " + svr + " command " + createFLBPayload(command) + " tagged as " + tag);
        try {
            client = ClientBuilder.newClient();
            client.target(svr).path(tag).request(MediaType.APPLICATION_JSON)
                    .buildPost(json(createFLBPayload(command))).invoke();

            client.close();
        } catch (Exception err) {
            LOGGER.warning(err.toString());
        } finally {
            if (client != null) {
                client.close();
            }
        }

    }

    private static class CheckerClass implements Runnable {
        private String id = "";
        private int retries = 0;
        private int delay = 0;
        Map<String, String> env = null;

        CheckerClass(String instanceId, int retries, int delaySecs, Map<String, String> env) {
            this.id = instanceId;
            this.retries = retries;
            this.delay = delaySecs * 1000;
            this.env = env;
            LOGGER.finer("Establishing Checker thread with id=" + this.id + " retries " + this.retries
                    + " delay Seconds = " + delaySecs);
        }

        /**
         * @param alertId
         * @return String
         */
        private FLBCommunication checkForAction(String alertId, Map<String, String> envs) {
            FLBCommunication comms = null;
            LOGGER.info("checkForAction commencing");
            try {
                SlackActions action = new SlackActions(envs);
                comms = action.checkForAction();
                if (comms.getFLBNode() != null) {
                    LOGGER.info("---------WE HAVE A NODE -----------" + comms.getFLBNode());
                }
                if (comms.getCommand() != null) {
                    LOGGER.info("---------WE HAVE A Command -----------" + comms.getCommand());
                }

            } catch (Exception error) {
                LOGGER.warning("checkForAction error:" + error.toString());
            }

            return comms;
        }

        @Override
        public void run() {
            int counter = 0;
            try {
                while (counter < retries) {
                    FLBCommunication action = checkForAction(id, env);
                    if ((action != null) && action.canAction()) {
                        signalFLBNode(action.getFLBNode(), action.getCommand(), id);
                        LOGGER.info("Actioning:\n" + action.summaryString());
                        break;
                    }
                    counter++;
                    Thread.sleep(delay);
                    // System.out.println("Thread ID: " + Thread.currentThread().threadId());
                    LOGGER.info(
                            "Thread ID: " + Thread.currentThread().getName() + ">>>>>>>>>>>>>> checked for command");
                }
            } catch (Throwable thrown) {
                LOGGER.warning("Checker Run error:" + thrown.toString());
            }
            LOGGER.info("Thread : " + Thread.currentThread().getName() + " ending <<<<<<<<<<<<<");

        }

    }

    FLBSocialCommandResource() {
        HelidonMdc.set("name", "FLBSocial");
        myEnvs.putAll(System.getenv());

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

        myEnvs = SlackActions.addProperties(myEnvs);
    }

    /**
     * @return String
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String getNoAlertId() {
        CheckerClass checker = new CheckerClass(dtf.format(LocalDateTime.now()), myRetryCount, myRetryDelay, myEnvs);
        checker.run();
        return "{getNoAlertId= " + checker.id + "}";

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
        // return "{postAlertNoId= " + checkForAction("") + "}";
        CheckerClass checker = new CheckerClass(dtf.format(LocalDateTime.now()), myRetryCount, myRetryDelay, myEnvs);
        checker.run();
        return "{postAlertNoId= " + checker.id + "}";

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
        // return "{PutNoAlertId= " + checkForAction("") + "}";
        CheckerClass checker = new CheckerClass(dtf.format(LocalDateTime.now()), myRetryCount, myRetryDelay, myEnvs);
        checker.run();
        return "{PutNoAlertId= " + checker.id + "}";

    }

    @Path("/{alertId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = FLBCommandMetrics.ALL_SOCIALS_NAME, absolute = true, description = FLBCommandMetrics.ALL_SOCIALS_DESCRIPTION)
    @Timed(name = FLBCommandMetrics.SOCIALS_TIMER_NAME, description = FLBCommandMetrics.SOCIALS_TIMER_DESCRIPTION, unit = MetricUnits.HOURS, absolute = true)
    public String postWithAlertId(@PathParam("alertId") String alertId) {

        // return "{postWithAlertId= " + checkForAction(alertId) + ", alertId=\"" +
        // alertId + "\"}";
        CheckerClass checker = new CheckerClass(dtf.format(LocalDateTime.now()), myRetryCount, myRetryDelay, myEnvs);
        checker.run();
        return "{postWithAlertId= " + checker.id + "}";

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
        boolean testFLB = Boolean.parseBoolean(myEnvs.getOrDefault(TESTFLB, "FALSE"));
        LOGGER.info("Test FLB allowed = " + testFLB);
        if (testFLB) {

            signalFLBNode(myEnvs.getOrDefault(TESTFLB_NODE, "localhost:8090"),
                    myEnvs.getOrDefault(TESTFLB_COMMAND, "test"), myEnvs.getOrDefault(TESTFLB_TAG, "command"));
        }
        return "{TestFLBDisabled= \"" + !testFLB + "\"}";

    }
}
