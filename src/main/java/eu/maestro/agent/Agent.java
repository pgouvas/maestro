package eu.maestro.agent;

import eu.maestro.util.Node;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;

/**
 *
 * @author Panagiotis Gouvas
 */
public class Agent implements Runnable {

    private static final Logger logger = Logger.getLogger(Agent.class.getName());

    private Node<String> tree;
    private String deploymentid;
    private String nodename;
    Cache<String, String> cache;
    //String
    public static final int STATUS_SPAWNING = 1;
    public static final int STATUS_INITIALIZED = 2;
    public static final int AGENT_SLEEP_CYCLE_MILLISECONDS = 1000;
    public static final int MAX_TIMEOUT_FOR_SERVICE_RUNNING_MILLISECONDS = 10000;

    //current state
    private boolean terminated = false;

    public Agent(Node<String> tree, String deploymentid, String nodename) {
        this.tree = tree;
        this.deploymentid = deploymentid;
        this.nodename = nodename;
        DefaultCacheManager cachemngr;
        try {
            cachemngr = new DefaultCacheManager("infinispan.xml");
            cache = cachemngr.getCache();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//EoCon

    @Override
    public void run() {

        logger.info("Starting Agent for " + deploymentid + "_" + nodename);

        //white to key-value
        String statuslabel = deploymentid + "_" + nodename + "_status";
        cache.put(statuslabel, "" + STATUS_INITIALIZED);

        //start timer
        long startTime = System.currentTimeMillis();

        while (!terminated) {

            terminated = hasTimeoutExceeded(startTime);

            //enter sleep mode
            try {
                if (!terminated) {
                    Thread.currentThread().sleep(AGENT_SLEEP_CYCLE_MILLISECONDS);
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }//terminated

        logger.info("Terminating Agent for " + deploymentid+ "_" + nodename);

    }//EoM

    private boolean hasTimeoutExceeded(long startTime) {
        boolean exceeded = false;
        long duration = System.currentTimeMillis() - startTime;
        if (duration > MAX_TIMEOUT_FOR_SERVICE_RUNNING_MILLISECONDS) {
            exceeded = true;
        }
        return exceeded;
    }//EoM        

}//EoC
