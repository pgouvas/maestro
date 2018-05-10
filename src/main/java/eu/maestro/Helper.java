package eu.maestro;

import eu.maestro.agent.Agent;
import eu.maestro.orchestrator.Orchestrator;
import eu.maestro.util.Node;
import eu.maestro.util.TreeUtil;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author Panagiotis Gouvas
 */
public class Helper {
        
    private static final Logger logger = Logger.getLogger(Helper.class.getName());
    
    public static void main(String[] args) throws InterruptedException, IOException {
        Node<String> tree = Helper.createSampleTree();
        Helper.bootOrchestrator(tree, "12345");
        //Tests
//        TreeUtil.printTree(tree);
//        Node<String> node = TreeUtil.searchTree(tree, "root");
//        if (node!=null) logger.info(node.toString());
//        List list = TreeUtil.listifyTree(tree);
//        logger.info(list.size()+"");        
    }//EoMain
    
    private static Node<String>  createSampleTree() {
        Node<String> root = new Node<>("root");
        Node<String> node1 = root.addChild(new Node<String>("node1"));
        Node<String> node11 = node1.addChild(new Node<String>("node11"));
        Node<String> node111 = node11.addChild(new Node<String>("node111"));
        Node<String> node112 = node11.addChild(new Node<String>("node112"));
        Node<String> node12 = node1.addChild(new Node<String>("node12"));
        Node<String> node2 = root.addChild(new Node<String>("node2"));
        Node<String> node21 = node2.addChild(new Node<String>("node21"));
        Node<String> node211 = node2.addChild(new Node<String>("node22"));
        return root;
    }//EoM

    private static void bootOrchestrator(Node<String> tree,String deploymentid){
        Orchestrator orchestrator = new Orchestrator(tree,deploymentid);
        Thread thread = new Thread(orchestrator);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }//EoC

}//EoC
