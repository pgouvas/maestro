package eu.maestro.agent;

import eu.maestro.util.Node;
import eu.maestro.util.TreeUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    DefaultCacheManager cachemngr;
    Cache<String, String> cache;
    private int status = 1;
    //Status
    public static final int STATUS_SPAWNING = 1;
    public static final int STATUS_INITIALIZED = 2;
    public static final int STATUS_IMAGEDOWNLOADED = 3;
    public static final int STATUS_WAITING_FOR_DEPENDENCIES = 4;
    public static final int STATUS_TRIGGERED_CONTAINER_START = 5;
    public static final int STATUS_STARTED = 6;
    //Timeouts
    public static final int AGENT_SLEEP_CYCLE_MILLISECONDS = 2000;
    public static final int MAX_TIMEOUT_FOR_SERVICE_BECOME_OPERATIONAL = 30000;         //30 seconds
    public static final int EMULATE_MAX_DOWNLOADFILE_MILLISECONDS = 7000;              //10 seconds
    public static final int EMULATE_MAX_STARTIMAGE_MILLISECONDS = 2000;                 //2 seconds
    public static final int EMULATE_MAX_CHECKIMAGE_MILLISECONDS = 2000;                 //2 seconds

    //current state
    private boolean terminated = false;

    public Agent(Node<String> tree, String deploymentid, String nodename) {
        this.tree = tree;
        this.deploymentid = deploymentid;
        this.nodename = nodename;
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
        status = STATUS_INITIALIZED;
        pushStatus(deploymentid, nodename, status);
        //start timer
        long startTime = System.currentTimeMillis();

        //------------Download Image
        boolean downloaded = downloadImage();
        if (!downloaded) {
            //Exception thrown AND propagated to key-value store
        } else {
            status = STATUS_IMAGEDOWNLOADED;
            pushStatus(deploymentid, nodename, status);
        }

        //------------Manage Dependencies        
        List<Node<String>> deps = getDependencies();
        if (deps.size() > 0) {
            status = STATUS_WAITING_FOR_DEPENDENCIES;
            pushStatus(deploymentid, nodename, status);
            boolean depsresolved = false;
            while (!depsResolved(deps) && !hasTimeoutExceeded(startTime)) {
                //update status and enter sleep mode
                try {
                    if (!terminated) {
                        pushStatus(deploymentid, nodename, status);
                        Thread.currentThread().sleep(AGENT_SLEEP_CYCLE_MILLISECONDS);
                    }
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }//while
        } //else No dependencies

        //-------------Start Image
        boolean startingimage = startImage(); //this is asynch
        if (!startingimage) {
            //Exception thrown AND propagated to key-value store
        } else {
            status = STATUS_TRIGGERED_CONTAINER_START;
            pushStatus(deploymentid, nodename, status);
        }

        boolean servicestarted = checkImageStarted();
        if (!servicestarted) {
            //Exception thrown AND propagated to key-value store
        } else {
            status = STATUS_STARTED;
            pushStatus(deploymentid, nodename, status);
        }

        while (!terminated && !hasTimeoutExceeded(startTime)) {

            //update status and enter sleep mode
            try {
                if (!terminated) {
                    pushStatus(deploymentid, nodename, status);
                    Thread.currentThread().sleep(AGENT_SLEEP_CYCLE_MILLISECONDS);
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }//terminated

        logger.info("Terminating Agent for " + deploymentid + "_" + nodename);
        cachemngr.stop();
    }//EoM

    private boolean depsResolved(List<Node<String>> deps) {
        boolean depsresolved = false;
        List<Boolean> statuslist = new ArrayList<>();
        for (Node<String> dep : deps) {
            String statuslabel = deploymentid + "_" + dep.getData() + "_status";
            depsresolved = cache.get(statuslabel).equalsIgnoreCase(""+STATUS_STARTED);
            if (depsresolved==false) break;
        }        
        return depsresolved;
    }//EoM

    private List<Node<String>> getDependencies() {
        logger.info("tree: " + tree);
        logger.info("checking tree for |" + nodename + "|");
        Node<String> node = TreeUtil.searchTree(tree, nodename);
        logger.info("node found: " + node.toString());
        return node.getChildren();
    }//EoM

    private void pushStatus(String deploymentid, String nodename, int status) {
        String statuslabel = deploymentid + "_" + nodename + "_status";
        cache.put(statuslabel, "" + status);
    }//EoM

    private boolean hasTimeoutExceeded(long startTime) {
        boolean exceeded = false;
        long duration = System.currentTimeMillis() - startTime;
        if (duration > MAX_TIMEOUT_FOR_SERVICE_BECOME_OPERATIONAL) {
            exceeded = true;
        }
        return exceeded;
    }//EoM        

    private boolean downloadImage() {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_DOWNLOADFILE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return true;
    }//EoM

    private boolean startImage() {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_STARTIMAGE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return true;
    }//EoM    

    private boolean checkImageStarted() {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_CHECKIMAGE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return true;
    }//EoM        

}//EoC
