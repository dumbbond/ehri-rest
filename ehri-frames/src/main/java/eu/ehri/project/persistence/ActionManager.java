/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.persistence;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.events.EventLink;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.models.events.SystemEventQueue;
import eu.ehri.project.models.events.Version;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;

/**
 * Class for dealing with actions.
 * <p/>
 * Events are captured as a linked list with new events placed
 * at the head. The head event is connected to a node called the
 * {@link eu.ehri.project.models.events.SystemEventQueue}. Events
 * can have subjects (the thing the event is happening to) and
 * actioners (the person initiating the event.) A subject's events
 * and an actioner's actions likewise for a linked list so it is
 * possible to fetch new events easily and prevent having to sort
 * by timestamp, etc. Schematically, the graph thus formed looks
 * something like:
 * <p/>
 * <pre>
 * <code>
 * Actioner              SystemEventQueue             Subject
 * \/                        \/                      \/
 * [lifecycleAction]     [lifecycleActionStream]     [lifecycleEvent]
 * |                         |                       |
 * e3--[actionHasEvent]->-- Event 3 ---[hasEvent]--<--e3
 * \/                        \/                      \/
 * [lifecycleAction]         [lifecycleAction]       [lifecycleEvent]
 * |                         |                       |
 * e2--[actionHasEvent]->-- Event 2 ---[hasEvent]--<--e2
 * \/                        \/                      \/
 * [lifecycleAction]         [lifecycleAction]       [lifecycleEvent]
 * |                         |                       |
 * e1--[actionHasEvent]->-- Event 1 ---[hasEvent]--<--e1
 * </code>
 * </pre>
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public final class ActionManager {

    // Name of the global event root node, from whence event
    // streams propagate.
    private static final Logger logger = LoggerFactory.getLogger(ActionManager.class);

    public static final String GLOBAL_EVENT_ROOT = "globalEventRoot";
    public static final String DEBUG_TYPE = "_debugType";
    public static final String EVENT_LINK = "eventLink";
    public static final String LINK_TYPE = "_linkType";

    private final FramedGraph<?> graph;
    private final GraphManager manager;
    private final Frame scope;
    private final Serializer versionSerializer;
    private final BundleDAO dao;

    /**
     * Constructor with scope.
     *
     * @param graph The framed graph
     */
    public ActionManager(FramedGraph<?> graph, Frame scope) {
        this.graph = graph;
        this.manager = GraphManagerFactory.getInstance(graph);
        this.scope = Optional.fromNullable(scope).or(SystemScope.getInstance());
        this.versionSerializer = new Serializer.Builder(graph).dependentOnly().build();
        this.dao = new BundleDAO(graph);
    }

    /**
     * Constructor.
     *
     * @param graph The framed graph
     */
    public ActionManager(FramedGraph<?> graph) {
        this(graph, SystemScope.getInstance());
    }

    /**
     * EventContext is a handle to a particular action to which additional
     * subjects can be added.
     *
     * @author Mike Bryant (http://github.com/mikesname)
     */
    public class EventContext {
        private final ActionManager actionManager;
        private final Actioner actioner;
        private final EventTypes actionType;
        private final Optional<String> logMessage;
        private final Optional<Frame> versionFrame;
        private final Optional<Bundle> versionBundle;
        private final Set<AccessibleEntity> subjects;
        private final String timestamp;

        /**
         * Create a new event context.
         *  @param actionManager The action manager instance
         * @param actioner      The actioner
         * @param type          The event type
         * @param logMessage    An optional log message
         */
        EventContext(ActionManager actionManager,
                Actioner actioner,
                EventTypes type,
                String timestamp, Optional<String> logMessage,
                Optional<Frame> versionFrame,
                Optional<Bundle> versionBundle) {
            this.actionManager = actionManager;
            this.actionType = type;
            this.actioner = actioner;
            this.logMessage = logMessage;
            this.versionFrame = versionFrame;
            this.versionBundle = versionBundle;
            this.subjects = Sets.newHashSet();
            this.timestamp = timestamp;
        }

        /**
         * Get the event actioner.
         *
         * @return The actioner
         */
        public Actioner getActioner() {
            return this.actioner;
        }

        /**
         * Get the event context log message.
         *
         * @return The optional log message
         */
        public Optional<String> getLogMessage() {
            return this.logMessage;
        }

        /**
         * Return the subjects of this event.
         *
         * @return a set of subjects
         */
        public Set<AccessibleEntity> getSubjects() {
            return subjects;
        }

        /**
         * Create a snapshot of the given node's data.
         *
         * @param frame The subject node
         * @return This event context
         */
        public EventContext createVersion(Frame frame) {
            try {
                Bundle bundle = versionSerializer.vertexFrameToBundle(frame);
                return createVersion(frame, bundle);
            } catch (SerializationError serializationError) {
                throw new RuntimeException(serializationError);
            }
        }

        /**
         * Create a snapshot of the given node using the provided data. This is
         * useful when the node has already been changed at this point the snapshot
         * is taken.
         *
         * @param frame  The subject node
         * @param bundle A bundle of the node's data
         * @return This event context
         */
        public EventContext createVersion(Frame frame, Bundle bundle) {
            Bundle version = Bundle.Builder.withClass(EntityClass.VERSION)
                    .addDataValue(Ontology.VERSION_ENTITY_ID, frame.getId())
                    .addDataValue(Ontology.VERSION_ENTITY_CLASS, frame.getType())
                    .addDataValue(Ontology.VERSION_ENTITY_DATA, bundle.toJson())
                    .build();
            EventContext ctx = new EventContext(actionManager, actioner,
                    actionType, timestamp, logMessage,
                    Optional.of(frame), Optional.of(version));
            for (AccessibleEntity subject : subjects) {
                ctx.addSubjects(subject);
            }
            return ctx;
        }

        /**
         * Add subjects to an event.
         *
         * @param entities A set of event subjects
         * @return This event context
         */
        public EventContext addSubjects(AccessibleEntity... entities) {
            for (AccessibleEntity entity : entities) {
                if (!subjects.contains(entity)) {
                    subjects.add(entity);
                }
            }
            return this;
        }

        /**
         * Get the type of this event context.
         *
         * @return The event context type
         */
        public EventTypes getEventType() {
            return actionType;
        }

        /**
         * Flush this event log to the graph.
         */
        public SystemEvent commit() {
            Vertex vertex = getLinkNode(Ontology.ACTIONER_HAS_LIFECYCLE_ACTION);
            replaceAtHead(actioner.asVertex(), vertex,
                    Ontology.ACTIONER_HAS_LIFECYCLE_ACTION,
                    Ontology.ACTIONER_HAS_LIFECYCLE_ACTION, Direction.OUT);
            SystemEvent systemEvent = createGlobalEvent(timestamp, actionType, logMessage);
            addActionerLink(systemEvent.asVertex(), vertex);

            for (Frame entity : subjects) {
                Vertex subjectVertex = getLinkNode(
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT);
                replaceAtHead(entity.asVertex(), subjectVertex,
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT,
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT, Direction.OUT);
                addSubjectLink(systemEvent.asVertex(), subjectVertex);
            }

            // Create the version.
            if (versionBundle.isPresent() && versionFrame.isPresent()) {
                try {
                    Frame subject = versionFrame.get();
                    Bundle version = versionBundle.get();
                    Version ev = dao.create(version, Version.class);
                    replaceAtHead(subject.asVertex(), ev.asVertex(),
                            Ontology.ENTITY_HAS_PRIOR_VERSION,
                            Ontology.ENTITY_HAS_PRIOR_VERSION, Direction.OUT);
                    graph.addEdge(null, ev.asVertex(),
                            systemEvent.asVertex(), Ontology.VERSION_HAS_EVENT);
                } catch (ValidationError validationError) {
                    throw new RuntimeException(validationError);
                }
            }

            return systemEvent;
        }
    }

    /**
     * Get the latest global event.
     *
     * @return The latest event node
     */
    public SystemEvent getLatestGlobalEvent() {
        try {
            SystemEventQueue sys = manager.getFrame(GLOBAL_EVENT_ROOT, EntityClass.SYSTEM, SystemEventQueue.class);
            Iterable<SystemEvent> latest = sys.getSystemEvents();
            return latest.iterator().hasNext() ? latest.iterator().next() : null;
        } catch (ItemNotFound itemNotFound) {
            throw new RuntimeException("Fatal error: system node (id: 'system') was not found. " +
                    "Perhaps the graph was incorrectly initialised?");
        }
    }

    /**
     * Get an iterable of global events in most-recent-first order.
     *
     * @return A iterable of event nodes
     */
    public Iterable<SystemEvent> getLatestGlobalEvents() {
        try {
            SystemEventQueue queue = manager.getFrame(
                    GLOBAL_EVENT_ROOT, EntityClass.SYSTEM, SystemEventQueue.class);
            return queue.getSystemEvents();
        } catch (ItemNotFound itemNotFound) {
            throw new RuntimeException("Couldn't find system event queue!");
        }
    }

    /**
     * Create an action node describing something that user U has done.
     *
     * @param user       The actioner
     * @param type       The event type
     * @param logMessage An optional log message
     * @return An EventContext object
     */
    public EventContext newEventContext(Actioner user, EventTypes type, Optional<String> logMessage) {
        return new EventContext(this, user, type, getTimestamp(), logMessage,
                Optional.<Frame>absent(), Optional.<Bundle>absent());
    }

    /**
     * Create an action node describing something that user U has done.
     *
     * @param user       The actioner
     * @param type       The event type
     * @return An EventContext object
     */
    public EventContext newEventContext(Actioner user, EventTypes type) {
        return new EventContext(this, user, type, getTimestamp(), Optional.<String>absent(),
                Optional.<Frame>absent(), Optional.<Bundle>absent());
    }

    /**
     * Create an action for the given subject, user, and type.
     *
     * @param subject The subject node
     * @param user    The actioner
     * @param type    The event type
     * @return An EventContext object
     */
    public EventContext newEventContext(AccessibleEntity subject, Actioner user,
            EventTypes type) {
        return newEventContext(subject, user, type, Optional.<String>absent());
    }

    /**
     * Create an action node that describes what user U has done with subject S
     * via logMessage log.
     *
     * @param subject    The subjject node
     * @param user       The actioner
     * @param logMessage A log message
     * @return An EventContext object
     */
    public EventContext newEventContext(AccessibleEntity subject, Actioner user,
            EventTypes type, Optional<String> logMessage) {
        EventContext context = newEventContext(user, type, logMessage);
        context.addSubjects(subject);
        return context;
    }

    /**
     * Set the scope of this action.
     *
     * @param frame The current permission scope
     * @return A new ActionManager instance.
     */
    public ActionManager setScope(Frame frame) {
        return new ActionManager(graph,
                Optional.fromNullable(frame).or(SystemScope.getInstance()));
    }


    /**
     * Determine if two events are the same according to the following
     * definition:
     * <p/>
     * <ol>
     *     <li>They have the same scope and subject</li>
     *     <li>They have the same actioner</li>
     *     <li>They are both of the same type</li>
     *     <li>They have the same log message, if any</li>
     * </ol>
     * <p/>
     * This function allows filtering an event stream for duplicates,
     * like someone repeatedly updating the same item.
     *
     * @param event1 the first event
     * @param event2 the second event
     * @param timeDiffInSeconds the elapsed time between the events
     *
     * @return whether or not the events are effectively the same.
     */
    public static boolean canAggregate(SystemEvent event1, SystemEvent event2, int timeDiffInSeconds) {
        // NB: Fetching all these props and relations is potentially quite
        // costly, so we want to short-circuit and return early is possible,
        // starting with the least-costly to fetch attributes.
        EventTypes eventType1 = event1.getEventType();
        EventTypes eventType2 = event2.getEventType();
        if (eventType1 != null && eventType2 != null && !eventType1.equals(eventType2)) {
            return false;
        }

        String logMessage1 = event1.getLogMessage();
        String logMessage2 = event2.getLogMessage();
        if (logMessage1 != null && logMessage2 != null && !logMessage1.equals(logMessage2)) {
            return false;
        }

        if (timeDiffInSeconds > -1) {
            DateTime event1Time = DateTime.parse(event1.getTimestamp());
            DateTime event2Time = DateTime.parse(event2.getTimestamp());
            int timeDiff = Seconds.secondsBetween(event1Time, event2Time).getSeconds();
            if (timeDiff >= timeDiffInSeconds) {
                return false;
            }
        }

        Frame eventScope1 = event1.getEventScope();
        Frame eventScope2 = event2.getEventScope();
        if (eventScope1 != null && eventScope2 != null && !eventScope1.equals(eventScope2)) {
            return false;
        }

        AccessibleEntity entity1 = event1.getFirstSubject();
        AccessibleEntity entity2 = event2.getFirstSubject();
        if (entity1 != null && entity2 != null && !entity1.equals(entity2)) {
            return false;
        }

        Actioner actioner1 = event1.getActioner();
        Actioner actioner2 = event2.getActioner();
        if (actioner1 != null && actioner2 != null && !actioner1.equals(actioner2)) {
            return false;
        }

        // Okay, fall through...
        return true;
    }

    /**
     * Test if two events are sequential with the same actioner. This is used
     * for aggregating repeated events.
     *
     * @param first the first temporal event
     * @param second the second temporal event
     * @return whether events can be aggregated by user
     */
    public static boolean sequentialWithSameAccessor(SystemEvent first, SystemEvent second) {
        Vertex firstVertex = first.asVertex();
        Vertex secondVertex = second.asVertex();

        for (Vertex flink : firstVertex.getVertices(Direction.IN, Ontology.ACTION_HAS_EVENT)) {
            for (Vertex slink : secondVertex.getVertices(Direction.IN, Ontology.ACTION_HAS_EVENT)) {
                for (Edge chain : slink.getEdges(Direction.OUT, Ontology.ACTIONER_HAS_LIFECYCLE_ACTION)) {
                    return chain.getVertex(Direction.IN).equals(flink);
                }
            }
        }
        return false;
    }

    public static boolean sameAs(SystemEvent event1, SystemEvent event2) {
        return canAggregate(event1, event2, -1);
    }

    // Helpers.

    private SystemEvent createGlobalEvent(String timestamp, EventTypes type, Optional<String> logMessage) {
        // Create a global event and insert it at the head of the system queue. The
        // relationship from the *system* node to the new latest action is
        // *type* Stream.
        try {
            logger.trace("Creating global event root");
            Vertex system = manager.getVertex(GLOBAL_EVENT_ROOT, EntityClass.SYSTEM);
            Bundle ge = Bundle.Builder.withClass(EntityClass.SYSTEM_EVENT)
                    .addDataValue(Ontology.EVENT_TYPE, type.toString())
                    .addDataValue(Ontology.EVENT_TIMESTAMP, timestamp)
                    .addDataValue(Ontology.EVENT_LOG_MESSAGE, logMessage.or(""))
                    .build();
            SystemEvent ev = dao.create(ge, SystemEvent.class);
            if (!scope.equals(SystemScope.getInstance())) {
                ev.setEventScope(scope);
            }
            replaceAtHead(system, ev.asVertex(), Ontology.ACTIONER_HAS_LIFECYCLE_ACTION + "Stream", Ontology.ACTIONER_HAS_LIFECYCLE_ACTION, Direction.OUT);
            return ev;
        } catch (ItemNotFound e) {
            e.printStackTrace();
            throw new RuntimeException("Fatal error: system node (id: 'system') was not found. " +
                    "Perhaps the graph was incorrectly initialised?");
        } catch (ValidationError e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unexpected validation error creating action", e);
        }
    }

    /**
     * Create a link vertex. This we stamp with a descriptive
     * type purely for debugging purposes.
     */
    private Vertex getLinkNode(String linkType) {
        try {
            return dao.create(Bundle.Builder.withClass(EntityClass.EVENT_LINK)
                    .addDataValue(DEBUG_TYPE, EVENT_LINK)
                    .addDataValue(LINK_TYPE, linkType).build(),
                    EventLink.class).asVertex();
        } catch (ValidationError e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a subjectLinkNode node to an event.
     *
     * @param event           The event node
     * @param subjectLinkNode The subjectLinkNode node
     */
    private void addSubjectLink(Vertex event, Vertex subjectLinkNode) {
        graph.addEdge(null, subjectLinkNode, event, Ontology.ENTITY_HAS_EVENT);
    }

    private void addActionerLink(Vertex event, Vertex actionerLinkNode) {
        graph.addEdge(null, actionerLinkNode, event, Ontology.ACTION_HAS_EVENT);
    }

    /**
     * Given a vertex <em>head</em> that forms that start of a chain <em>relation</em> with
     * direction <em>direction</em>, insert vertex <em>insert</em> <strong>after</strong>
     * the head of the chain.
     *
     * @param head      The current vertex queue head node
     * @param newHead   The replacement head node
     * @param relation  The relationship between them
     * @param direction The direction of the relationship
     */
    private void replaceAtHead(Vertex head, Vertex newHead, String headRelation,
                               String relation, Direction direction) {
        Iterator<Vertex> iter = head.getVertices(direction, headRelation).iterator();
        if (iter.hasNext()) {
            Vertex current = iter.next();
            for (Edge e : head.getEdges(direction, headRelation)) {
                graph.removeEdge(e);
            }
            graph.addEdge(null, newHead, current, relation);

        }
        graph.addEdge(null, head, newHead, headRelation);
    }

    /**
     * Get the current time as a timestamp.
     *
     * @return The current time in ISO DateTime format.
     */
    public static String getTimestamp() {
        return ISODateTimeFormat.dateTime().print(DateTime.now());
    }
}
