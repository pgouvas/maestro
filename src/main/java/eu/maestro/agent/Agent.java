package eu.maestro.agent;

import eu.maestro.agent.exception.DownloadImageException;
import eu.maestro.agent.exception.ImageRuntimeException;
import eu.maestro.agent.exception.InitializationException;
import eu.maestro.agent.exception.StartImageException;
import eu.maestro.agent.exception.StatusCommitException;
import eu.maestro.agent.exception.TimeOutException;
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
    //ERROR status
    public static final int STATUS_TERMINATED_DUE_TO_TIMEOUT = -1;
    public static final int STATUS_TERMINATED_DUE_TO_UNSUCCESSFUL_INITIALIZATION = -2;
    public static final int STATUS_TERMINATED_DUE_TO_UNSUCCESSFUL_DOWNLOAD = -3;
    public static final int STATUS_TERMINATED_DUE_TO_UNSUCCESSFUL_CONTAINER_TRIGGERING = -5;
    public static final int STATUS_TERMINATED_DUE_TO_BAD_LAUNCH = -6;

    //Timeouts
    long startTime = 0;
    public static final int AGENT_SLEEP_CYCLE_MILLISECONDS = 100;
    public static final int TIMEOUT_FOR_SERVICE_TO_BECOME_OPERATIONAL = 30000;         //30 seconds
    public static final int TIMEOUT_FOR_COMMIT_IN_KEY_VALUE_STORE = 10000;             //10 seconds

    public static final int EMULATE_MAX_INITIALIZATION_TIME_MILLISECONDS = 1000;              //10 seconds
    public static final int EMULATE_MAX_DOWNLOADFILE_MILLISECONDS = 7000;              //10 seconds
    public static final int EMULATE_MAX_STARTIMAGE_MILLISECONDS = 1000;                //2 seconds
    public static final int EMULATE_MAX_CHECKIMAGE_MILLISECONDS = 1000;                //2 seconds

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
        startTime = System.currentTimeMillis();
    }//EoCon

    private void setStatus(int status) {
        try {
            commitStatus(deploymentid, nodename, status);
        } catch (StatusCommitException ex) {
            Logger.getLogger(Agent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//EoM

    private boolean isValueNull(String key) {
        boolean isnull = false;
        if (cache.get(key) == null) {
            isnull = true;
        }
        return isnull;
    }//EoM

    private void commitStatus(String deploymentid, String nodename, int status) throws StatusCommitException {
        String statuslabel = deploymentid + "_" + nodename + "_status";
        boolean timedout = false;
        do {
            try {
                if (timedout) {
                    throw new StatusCommitException();
                }
                cache.put(statuslabel, "" + status);
                Thread.currentThread().sleep(AGENT_SLEEP_CYCLE_MILLISECONDS);
            } //while
            catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } while (!isValueNull(statuslabel) && !cache.get(statuslabel).equalsIgnoreCase("" + status) && !(timedout = hasTimeoutExceeded(TIMEOUT_FOR_SERVICE_TO_BECOME_OPERATIONAL))); //while
        //logger.info("SUCCESSFULL commit of "+statuslabel +" -> "+status);
    }//EoM    

    //check consul/netdata/jre / docker iterface /  
    private void step2Initialize() throws InitializationException, TimeOutException {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_INITIALIZATION_TIME_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new InitializationException();
        }
    }//EoM        

    private void step3DownloadImage() throws DownloadImageException, TimeOutException {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_DOWNLOADFILE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new DownloadImageException();
        }
    }//EoM    

    private void step5StartImage() throws StartImageException, TimeOutException {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_STARTIMAGE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new StartImageException();
        }
    }//EoM       

    private void step6CheckRunningImage() throws ImageRuntimeException, TimeOutException {
        try {
            Thread.currentThread().sleep(EMULATE_MAX_CHECKIMAGE_MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new ImageRuntimeException();
        }
    }//EoM      

    @Override
    public void run() {
        //------------Step 1 has already been accomplished. VM is booted and Agent Started
        logger.info("Starting Agent for " + deploymentid + "_" + nodename);

        //------------Step 2: Initialize Image
        try {
            //VM has been spawned. We will proceed with the initialization
            step2Initialize();
            setStatus(status = STATUS_INITIALIZED);
        } catch (InitializationException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (TimeOutException ex) {
            logger.log(Level.SEVERE, null, ex);
            setStatus(status = STATUS_TERMINATED_DUE_TO_TIMEOUT);
        }

        //------------Step 3: Download Image
        if (status > 0) {
            try {
                step3DownloadImage();
                setStatus(status = STATUS_IMAGEDOWNLOADED);
            } catch (DownloadImageException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_UNSUCCESSFUL_DOWNLOAD);
            } catch (TimeOutException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_TIMEOUT);
            }
        }

        //-----------Step 4: Manage dependencies if no fatal exception has been thrown
        if (status > 0) {
            List<Node<String>> deps = getDependencies();
            if (deps.size() > 0) {
                setStatus(status = STATUS_WAITING_FOR_DEPENDENCIES);
                boolean depsresolved = false;
                boolean timedout = false;
                while (!(depsresolved = depsResolved(deps)) && !(timedout = hasTimeoutExceeded(TIMEOUT_FOR_SERVICE_TO_BECOME_OPERATIONAL))) {
                    try {
                        if (!terminated) {
//                        commitStatus(deploymentid, nodename, status);
                            Thread.currentThread().sleep(AGENT_SLEEP_CYCLE_MILLISECONDS);
                        }
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }//while
            } //else No dependencies            
        }//manage dependencies    

        //-------------Step 5: Start Image
        if (status > 0) {
            try {
                step5StartImage();
                setStatus(status = STATUS_TRIGGERED_CONTAINER_START);
            } catch (StartImageException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_UNSUCCESSFUL_CONTAINER_TRIGGERING);
            } catch (TimeOutException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_TIMEOUT);
            }
        }

        //-------------Step 6: Verify Image Started
        if (status > 0) {
            try {
                step6CheckRunningImage();
                setStatus(status = STATUS_STARTED);
            } catch (ImageRuntimeException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_BAD_LAUNCH);
            } catch (TimeOutException ex) {
                logger.log(Level.SEVERE, null, ex);
                setStatus(status = STATUS_TERMINATED_DUE_TO_TIMEOUT);
            }
        }

        while (!terminated && !hasTimeoutExceeded(TIMEOUT_FOR_SERVICE_TO_BECOME_OPERATIONAL)) {
            //update status and enter sleep mode
            try {
                if (!terminated) {
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
        for (Node<String> dep : deps) {
            String statuslabel = deploymentid + "_" + dep.getData() + "_status";
            depsresolved = cache.get(statuslabel).equalsIgnoreCase("" + STATUS_STARTED);
            if (depsresolved == false) {
                break;
            }
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

    private boolean hasTimeoutExceeded(long timeout) {
        boolean exceeded = false;
        long duration = System.currentTimeMillis() - startTime;
        if (duration > timeout) {
            exceeded = true;
        }
        return exceeded;
    }//EoM  

    private boolean hasTimeoutExceeded(long startTime, long timeout) {
        boolean exceeded = false;
        long duration = System.currentTimeMillis() - startTime;
        if (duration > timeout) {
            exceeded = true;
        }
        return exceeded;
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
