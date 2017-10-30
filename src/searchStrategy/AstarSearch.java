package searchStrategy;


import java.util.*;

import MapSupport.Coord;
import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;
import searchStrategy.graph.NodeComparator;

/**
 * 
 * @author luisfisherjr
 * 
 * A* search implementation using SearchStrategy as interface
 */

public class AstarSearch implements SearchStrategy{

    // D is a scale value for you to adjust performance vs accuracy
    private final double D = 1.5;
    
    @Override
    public List<Edge> search(Graph graph, Node source, Node destination) {

    	if (source.equals(destination)) {
    		List<Edge> sameNode = new ArrayList<Edge>();
    		sameNode.add(new Edge(source, destination, 0));
    		return sameNode; 
    	}
    	
        Queue<Node> frontier = new PriorityQueue<>(new NodeComparator());
        Set<Node> exploredSet = new HashSet<Node>();

        Node parent = null;

        Collection<Node> nodeCollection = graph.getNodes();
        
        // initialize g, h, parent for all nodes
        for(Node node: nodeCollection) {

            node.parent = null;
            node.g = Double.POSITIVE_INFINITY;
            node.h = heuristicManhatten(node, destination);

            // set source g value
            if (node.equals(source)) {
            	parent = node;
                node.g = 0;
            }
        }

        // add to frontier
        frontier.offer(parent);
       
        double tempGScore;

        while(!frontier.isEmpty()) {

            // remove node from frontier and add to explored set
            parent = frontier.poll();
            exploredSet.add(parent);

            // return path found
            if (parent.equals(destination)) {
                return constructPath(graph, parent);
            }

            for(Node child: graph.neighbors(parent)) {

                // skips previously explored paths
                if (exploredSet.contains(child)) {
                    continue;
                }

                tempGScore = parent.g + graph.distance(parent, child);
                if (tempGScore >= child.g) {
                    continue;// skip because we are at the worse path
                }
                else if (!frontier.contains(child)) {
                    child.parent = parent;
                    child.g = tempGScore;
                    frontier.offer(child);
                }
            }
        }
        return null;
    }

    // uses manhatten distance
    private double heuristicManhatten(Node node, Node destination) {

        Coord nodeData = (Coord)node.getData();
        Coord destinationData = (Coord) destination.getData();

        int dx = Math.abs(nodeData.xpos - destinationData.xpos);
        int dy = Math.abs(nodeData.ypos - destinationData.ypos);
        
        return D * dx + dy;
    }

    private List<Edge> constructPath(Graph graph, Node destination) {

        List<Edge> answer = new ArrayList<Edge>();
        Node node = destination;

        while (node.parent != null) {

            answer.add(new Edge(node.parent, node,
                    graph.distance(node.parent, node)));

            node = node.parent;
        }

        Collections.reverse(answer);
 
        return answer;
    }
}