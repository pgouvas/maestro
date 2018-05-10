package eu.maestro.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author Panagiotis Gouvas
 *
 */
public class TreeUtil {

    private static final Logger logger = Logger.getLogger(TreeUtil.class.getName());

    public static Node<String> searchTree(Node<String> node, String nodename) {
        if (((Node<String>) node).getData().equals(nodename)) {
            return node;
        } else {
            for (Node nd : node.getChildren()) {
                return searchTree(nd, nodename);
            }
        }
        return null;
    }//EoM

    public static List<String> listifyTree(Node<String> node) {
        List<String> list = new ArrayList<>();
        list.add(node.getData());
        for (Node nd : node.getChildren()) {
            appendtoListedTree(nd, list);
        }
        return list;
    }//EoM    

    private static List<String> appendtoListedTree(Node<String> node, List<String> list) {
        list.add(node.getData());
        for (Node nd : node.getChildren()) {
            appendtoListedTree(nd, list);
        }
        return list;
    }//EoM       

    public static <T> void printTree(Node<T> node) {
        logger.info((String) node.getData());
        node.getChildren().forEach(each -> printTree(each));
    }//EoM    

}//EoClass
