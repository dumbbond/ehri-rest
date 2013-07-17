package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.AnnotatableEntity;
import eu.ehri.project.models.base.Annotator;

@EntityType(EntityClass.ANNOTATION)
public interface Annotation extends AnnotatableEntity, AccessibleEntity {

    @Fetch(Ontology.ANNOTATION_ANNOTATES)
    @Adjacency(label = Ontology.ANNOTATION_ANNOTATES, direction = Direction.IN)
    public Iterable<Annotation> getAnnotations();

    @Fetch(value = Ontology.ANNOTATOR_HAS_ANNOTATION, depth = 1)
    @Adjacency(label = Ontology.ANNOTATOR_HAS_ANNOTATION, direction = Direction.IN)
    public Annotator getAnnotator();

    @Adjacency(label = Ontology.ANNOTATOR_HAS_ANNOTATION, direction = Direction.IN)
    public Annotator setAnnotator(final Annotator annotator);

    @Adjacency(label = Ontology.ANNOTATION_ANNOTATES)
    public Iterable<AnnotatableEntity> getTargets();

    @Adjacency(label = Ontology.ANNOTATION_HAS_SOURCE)
    public void addSource(final Annotator annotator);

    @Property(Ontology.ANNOTATION_NOTES_BODY)
    public String getBody();
}
