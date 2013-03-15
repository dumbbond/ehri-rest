package eu.ehri.project.importers.old;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.ImportCallback;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.base.TemporalEntity;
import eu.ehri.project.models.idgen.AccessibleEntityIdGenerator;
import eu.ehri.project.models.idgen.IdGenerator;
import eu.ehri.project.persistance.Bundle;
import eu.ehri.project.persistance.BundleDAO;

/**
 * Base class for importers that import documentary units, with their
 * constituent logical data, description(s), and date periods.
 * 
 * @author michaelb
 * 
 * @param <T>
 */
public abstract class AbstractImporter<T> {

    protected final Repository repository;
    protected final FramedGraph<Neo4jGraph> framedGraph;
    protected final GraphManager manager;
    protected final ImportLog log;
    protected final T documentContext;
    protected List<ImportCallback> createCallbacks = new LinkedList<ImportCallback>();
    protected List<ImportCallback> updateCallbacks = new LinkedList<ImportCallback>();

    /**
     * Constructor.
     * 
     * @param framedGraph
     * @param repository
     * @param log
     * @param documentContext
     */
    public AbstractImporter(FramedGraph<Neo4jGraph> framedGraph,
            Repository repository, ImportLog log, T documentContext) {
        this.repository = repository;
        this.framedGraph = framedGraph;
        this.log = log;
        this.documentContext = documentContext;
        manager = GraphManagerFactory.getInstance(framedGraph);
    }

    /**
     * Add a callback to run when an item is created.
     * 
     * @param cb
     */
    public void addCreationCallback(final ImportCallback cb) {
        createCallbacks.add(cb);
    }

    /**
     * Add a callback to run when an item is updated.
     * 
     * @param cb
     */
    public void addUpdateCallback(final ImportCallback cb) {
        updateCallbacks.add(cb);
    }

    /**
     * Extract the logical DocumentaryUnit at a given depth.
     * 
     * @param itemData
     * @param depth
     * @return
     * @throws ValidationError
     */
    protected abstract Map<String, Object> extractDocumentaryUnit(T itemData,
            int depth) throws ValidationError;

    /**
     * Extract DocumentDescriptions at a given depth from the input data.
     * 
     * @param itemData
     * @param depth
     * @return
     * @throws ValidationError
     */
    protected abstract Iterable<Map<String, Object>> extractDocumentDescriptions(
            T itemData, int depth) throws ValidationError;

    /**
     * Extract a list of DatePeriod bundles from an item's data.
     * 
     * @param data
     * @return
     */
    public abstract Iterable<Map<String, Object>> extractDates(T data);

    /**
     * Import a single archdesc or c01-12 item, keeping a reference to the
     * hierarchical depth.
     * 
     * @param itemData
     * @param parent
     * @param depth
     * @throws ValidationError
     * @throws IntegrityError
     */
    protected DocumentaryUnit importItem(T itemData, DocumentaryUnit parent,
            int depth) throws ValidationError, IntegrityError {
        Bundle unit = new Bundle(EntityClass.DOCUMENTARY_UNIT,
                extractDocumentaryUnit(itemData, depth));
        BundleDAO persister = new BundleDAO(framedGraph, repository);

        // Add dates and descriptions to the bundle since they're @Dependent
        // relations.
        for (Map<String, Object> dpb : extractDates(itemData)) {
            unit = unit.withRelation(TemporalEntity.HAS_DATE, new Bundle(
                    EntityClass.DATE_PERIOD, dpb));
        }
        for (Map<String, Object> dpb : extractDocumentDescriptions(itemData,
                depth)) {
            unit = unit.withRelation(Description.DESCRIBES, new Bundle(
                    EntityClass.DOCUMENT_DESCRIPTION, dpb));
        }

        PermissionScope scope = parent != null ? parent : repository;
        IdGenerator generator = AccessibleEntityIdGenerator.INSTANCE;
        String id = generator.generateId(EntityClass.DOCUMENTARY_UNIT, scope, unit);
        boolean exists = manager.exists(id);
        DocumentaryUnit frame = persister.createOrUpdate(unit.withId(id),
                DocumentaryUnit.class);

        // Set the repository/item relationship
        frame.setRepository(repository);
        frame.setPermissionScope(scope);
        // Set the parent child relationship
        if (parent != null)
            parent.addChild(frame);

        // Run creation callbacks for the new item...
        if (exists) {
            for (ImportCallback cb : updateCallbacks) {
                cb.itemImported(frame);
            }
        } else {
            for (ImportCallback cb : createCallbacks) {
                cb.itemImported(frame);
            }
        }
        return frame;
    }
}