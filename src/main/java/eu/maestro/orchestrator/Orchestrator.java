package eu.maestro.orchestrator;

import eu.maestro.agent.Agent;
import static eu.maestro.agent.Agent.STATUS_SPAWNING;
import eu.maestro.util.Node;
import eu.maestro.util.TreeUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;

/**
 *
 * @author Panagiotis Gouvas
 */
public class Orchestrator implements Runnable {
    
    private static final Logger logger = Logger.getLogger(Orchestrator.class.getName());
    private Node<String> tree;
    private String deploymentid;
    Cache<String, String> cache;

    //Global Strings
    public static final int ORCHESTRATOR_SLEEP_CYCLE_MILLISECONDS = 1000;    
    public static final int MAX_TIMEOUT_FOR_DEPLOYMENT_MILLISECONDS = 20000;

    //current state
    private boolean terminated = false;
    
    public Orchestrator(Node<String> tree, String deploymentid) {
        this.tree = tree;
        this.deploymentid = deploymentid;
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
        logger.info("Starting Orchestrator Thread for " + deploymentid);
        //boot all VMS
        List<String> nodes = TreeUtil.listifyTree(tree);
        for (String nodename : nodes) {
            Agent agent = new Agent(tree, deploymentid, nodename);
            Thread thread = new Thread(agent);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
            //white to key-value
            String statuslabel = deploymentid + "_" + nodename + "_status";
            cache.put(statuslabel, "" + STATUS_SPAWNING);
        }//for

        //start timer
        long startTime = System.currentTimeMillis();

        //initiate control loop
        while (!terminated) {
            Map<String, String> statusmap = new HashMap<>();
            for (String nodename : nodes) {
                String statuslabel = deploymentid + "_" + nodename + "_status";
                String status = cache.get(statuslabel);
                statusmap.put(nodename, status);
            }//for

            logger.info("Status of Nodes: "+statusmap.toString());
            
            terminated = areAllCompleted(statusmap);
            
            terminated = hasTimeoutExceeded(startTime);

            //enter sleep mode
            try {
                if (!terminated) {
                    Thread.currentThread().sleep(ORCHESTRATOR_SLEEP_CYCLE_MILLISECONDS);
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }//terminated
        
        logger.info("Terminating Orchestrator for " + deploymentid);
        
    }//EoM

    private boolean areAllCompleted(Map<String, String> statusmap) {
        boolean completed = false;
        //TODO
        return completed;
    }//EoM

    private boolean hasTimeoutExceeded(long startTime) {
        boolean exceeded = false;
        long duration = System.currentTimeMillis() - startTime;
        if (duration > MAX_TIMEOUT_FOR_DEPLOYMENT_MILLISECONDS) {
            exceeded = true;
        }
        return exceeded;
    }//EoM    
    
}//EoC
