package eu.ehri.project.acl.wrapper;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.pipes.PipeFunction;
import eu.ehri.project.acl.AclManager;


public class AclEdge extends AclElement implements Edge {
    private final PipeFunction<Vertex,Boolean> aclFilter;

    protected AclEdge(Edge baseEdge, AclGraph<?> aclGraph) {
        super(baseEdge, aclGraph);
        aclFilter = AclManager.getAclFilterFunction(aclGraph.getAccessor());
    }

    @Override
    public Vertex getVertex(Direction direction) throws IllegalArgumentException {
        Vertex vertex = ((Edge) baseElement).getVertex(direction);
        return aclFilter.compute(vertex) ? new AclVertex(((Edge) baseElement).getVertex(direction), graph) : null;
    }

    @Override
    public String getLabel() {
        return ((Edge) this.baseElement).getLabel();
    }

    public Edge getBaseEdge() {
        return (Edge) this.baseElement;
    }
}