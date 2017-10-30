package searchStrategy;

import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;

import java.util.List;
import java.util.Set;

import enums.Terrain;

/**
 * @author luisfisherjr
 * 
 * SearchStrategy is intended to be a interface for all graph based searches
 */


public interface SearchStrategy {
    public List<Edge> search(Graph graph,  Node source, Node destination);
}