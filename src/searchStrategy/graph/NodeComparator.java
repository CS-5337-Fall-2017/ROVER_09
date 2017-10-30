package searchStrategy.graph;

import java.util.Comparator;

public class NodeComparator implements Comparator<Node> {

    @Override
    public int compare(Node node, Node destination){

        double f1 = node.g + node.h;
        double f2 = destination.g + destination.h;

        return (int) (f1 - f2);
    }
}