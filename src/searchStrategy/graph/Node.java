package searchStrategy.graph;

/**
 * 
 * @author luisfisherjr
 * 
 * Node used inside AdjacencyList, Edge and SearchStrategy classes
 */

public class Node<T> {
    private final T data;

    // used for search and compare
    public double g = Double.POSITIVE_INFINITY;
    public double h = 0;
    public Node<T> parent = null;

    // used for alpha beta pruning
    public int alpha;
    public int beta;

    public Node(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Node{" +
            "data=" + data +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;

        Node<?> node = (Node<?>) o;

        return getData() != null ? getData().equals(node.getData()) : node.getData() == null;

    }

    @Override
    public int hashCode() {
        return getData() != null ? getData().hashCode() : 0;
    }
}