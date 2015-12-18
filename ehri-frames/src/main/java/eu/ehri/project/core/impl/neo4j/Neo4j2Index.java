package eu.ehri.project.core.impl.neo4j;

import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Index;
import com.tinkerpop.blueprints.Parameter;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.StringFactory;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;

import java.util.HashMap;
import java.util.Map;



public class Neo4j2Index<T extends Neo4j2Element, S extends PropertyContainer> implements Index<T> {

    private final Class<T> indexClass;
    protected final Neo4j2Graph graph;
    private final String indexName;
    protected org.neo4j.graphdb.index.Index<S> rawIndex;

    protected Neo4j2Index(String indexName, Class<T> indexClass, Neo4j2Graph graph, Parameter... indexParameters) {
        this.indexClass = indexClass;
        this.graph = graph;
        this.indexName = indexName;
        this.generateIndex(indexParameters);
    }

    public Class<T> getIndexClass() {
        if (Vertex.class.isAssignableFrom(this.indexClass))
            return (Class) Vertex.class;
        else
            return (Class) Edge.class;
    }

    public String getIndexName() {
        return this.indexName;
    }

    public void put(String key, Object value, T element) {
        try {
            this.graph.autoStartTransaction(true);
            this.rawIndex.add((S) element.getRawElement(), key, value);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The underlying Neo4j graph does not natively support this method within a transaction.
     * If the graph is not currently in a transaction, then the operation runs efficiently.
     * If the graph is in a transaction, then, for every element, a try/catch is used to determine if its in the current transaction.
     */
    public CloseableIterable<T> get(String key, Object value) {
        this.graph.autoStartTransaction(false);
        IndexHits<S> itty = this.rawIndex.get(key, value);
        if (this.indexClass.isAssignableFrom(Neo4j2Vertex.class))
            return new Neo4j2VertexIterable(itty, this.graph);
        return new Neo4j2EdgeIterable(itty, this.graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The underlying Neo4j graph does not natively support this method within a transaction.
     * If the graph is not currently in a transaction, then the operation runs efficiently.
     * If the graph is in a transaction, then, for every element, a try/catch is used to determine if its in the current transaction.
     */
    public CloseableIterable<T> query(String key, Object query) {
        this.graph.autoStartTransaction(false);
        IndexHits<S> itty = this.rawIndex.query(key, query);
        if (this.indexClass.isAssignableFrom(Neo4j2Vertex.class))
            return new Neo4j2VertexIterable(itty, this.graph);
        return new Neo4j2EdgeIterable(itty, this.graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The underlying Neo4j graph does not natively support this method within a transaction.
     * If the graph is not currently in a transaction, then the operation runs efficiently.
     * If the graph is in a transaction, then, for every element, a try/catch is used to determine if its in the current transaction.
     */
    public CloseableIterable<T> query(Object query) {
        this.graph.autoStartTransaction(false);
        IndexHits<S> itty = this.rawIndex.query(query);
        if (this.indexClass.isAssignableFrom(Neo4j2Vertex.class))
            return new Neo4j2VertexIterable(itty, this.graph);
        return new Neo4j2EdgeIterable(itty, this.graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The underlying Neo4j graph does not natively support this method within a transaction.
     * If the graph is not currently in a transaction, then the operation runs efficiently.
     * If the graph is in a transaction, then, for every element, a try/catch is used to determine if its in the current transaction.
     */
    public long count(String key, Object value) {
        this.graph.autoStartTransaction(false);
        if (!this.graph.checkElementsInTransaction()) {
            IndexHits hits = this.rawIndex.get(key, value);
            long count = hits.size();
            hits.close();
            return count;
        } else {
            CloseableIterable<T> hits = this.get(key, value);
            long count = 0;
            for (T t : hits) {
                count++;
            }
            hits.close();
            return count;
        }
    }

    public void remove(String key, Object value, T element) {
        try {
            this.graph.autoStartTransaction(true);
            this.rawIndex.remove((S) element.getRawElement(), key, value);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void generateIndex(Parameter<Object, Object>... indexParameters) {
        this.graph.autoStartTransaction(true);
        IndexManager manager = this.graph.getRawGraph().index();
        if (Vertex.class.isAssignableFrom(this.indexClass)) {
            if (indexParameters.length > 0)
                this.rawIndex = (org.neo4j.graphdb.index.Index<S>) manager.forNodes(this.indexName, generateParameterMap(indexParameters));
            else
                this.rawIndex = (org.neo4j.graphdb.index.Index<S>) manager.forNodes(this.indexName);
        } else {
            if (indexParameters.length > 0)
                this.rawIndex = (org.neo4j.graphdb.index.Index<S>) manager.forRelationships(this.indexName, generateParameterMap(indexParameters));
            else
                this.rawIndex = (org.neo4j.graphdb.index.Index<S>) manager.forRelationships(this.indexName);
        }
    }

    public String toString() {
        return StringFactory.indexString(this);
    }

    private static Map<String, String> generateParameterMap(Parameter<Object, Object>... indexParameters) {
        Map<String, String> map = new HashMap<String, String>();
        for (Parameter<Object, Object> parameter : indexParameters) {
            map.put(parameter.getKey().toString(), parameter.getValue().toString());
        }
        return map;
    }

    public org.neo4j.graphdb.index.Index<S> getRawIndex() {
        return this.rawIndex;
    }
}
