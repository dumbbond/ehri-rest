package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Incidence;

import eu.ehri.project.relationships.Annotates;


public interface AnnotatableEntity {
    @Incidence(label=Annotation.ANNOTATES, direction=Direction.IN)
    public Iterable<Annotates> getAnnotationContexts();
    
    @Incidence(label=Annotation.ANNOTATES, direction=Direction.IN)
    public void addAnnotationContext(final Annotates annotates);

    @Adjacency(label=Annotation.ANNOTATES, direction=Direction.IN)
    public Iterable<Annotation> getAnnotations();
    
    @Adjacency(label=Annotation.ANNOTATES, direction=Direction.IN)
    public void addAnnotation(final Annotation annotation);
}
