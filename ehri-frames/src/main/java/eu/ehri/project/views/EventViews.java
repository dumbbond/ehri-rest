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

package eu.ehri.project.views;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;
import eu.ehri.project.acl.AclManager;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.base.Watchable;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.utils.pipes.AggregatorPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * View class for handling event streams.
 */
public class EventViews {

    private static final Logger logger = LoggerFactory.getLogger(EventViews.class);

    // Maximum number of events to be aggregated in a single batch
    private static final int MAX_AGGREGATION = 200;

    private final FramedGraph<?> graph;
    private final GraphManager manager;
    private final ActionManager actionManager;
    private final int offset;
    private final int limit;
    private final Set<String> users;
    private final Set<String> ids;
    private final Set<EntityClass> entityTypes;
    private final Set<EventTypes> eventTypes;
    private final Optional<String> from;
    private final Optional<String> to;
    private final Set<ShowType> showType;
    private final Aggregation aggregation;

    // Aggregator function that aggregates adjacent events by 'strict' similarity,
    // which in practice means:
    // - same event type
    // - same subject(s)
    // - same actioner
    private static final AggregatorPipe.AggregatorFunction<SystemEvent> strictAggregator = new AggregatorPipe
            .AggregatorFunction<SystemEvent>() {
        @Override
        public boolean aggregate(SystemEvent a, SystemEvent b, int count) {
            return ActionManager.sameAs(a, b);
        }
    };

    // Aggregator function that aggregates adjacent events by actioner
    private static final AggregatorPipe.AggregatorFunction<SystemEvent> userAggregator = new AggregatorPipe
            .AggregatorFunction<SystemEvent>() {
        @Override
        public boolean aggregate(SystemEvent a, SystemEvent b, int count) {
            return count < MAX_AGGREGATION && ActionManager.sequentialWithSameAccessor(b, a);
        }
    };

    // Discriminator for personalised events
    public enum ShowType {
        watched, followed
    }

    // Discriminator for aggregation type
    public enum Aggregation {
        user, strict, off
    }

    public static class Builder {
        private FramedGraph<?> graph;
        private int offset = -1;
        private int limit = -1;
        private Set<String> users = Sets.newHashSet();
        private Set<String> ids = Sets.newHashSet();
        private Set<EntityClass> entityTypes = Sets.newHashSet();
        private Set<EventTypes> eventTypes = Sets.newHashSet();
        private Optional<String> from = Optional.absent();
        private Optional<String> to = Optional.absent();
        private Set<ShowType> showType = Sets.newHashSet();
        private Aggregation aggregation = Aggregation.strict;

        public Builder(FramedGraph<?> graph) {
            this.graph = graph;
        }

        public Builder withRange(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
            return this;
        }

        public Builder withUsers(String... users) {
            this.users.addAll(Lists.newArrayList(users));
            return this;
        }

        public Builder withIds(String... ids) {
            this.ids.addAll(Lists.newArrayList(ids));
            return this;
        }

        public Builder withEntityTypes(String... entities) {
            for (String entity: entities) {
                try {
                    this.entityTypes.add(EntityClass.withName(entity));
                } catch (NoSuchElementException e) {
                    logger.warn("Invalid entity type: " + entity);
                }
            }
            return this;
        }

        public Builder withEventTypes(String... eventTypes) {
            for (String eventType: eventTypes) {
                try {
                    this.eventTypes.add(EventTypes.valueOf(eventType));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid event type: " + eventType);
                }
            }
            return this;
        }

        public Builder from(String from) {
            this.from = Optional.fromNullable(from);
            return this;
        }

        public Builder to(String to) {
            this.to = Optional.fromNullable(to);
            return this;
        }

        public Builder withShowType(String... showTypes) {
            for (String showType: showTypes) {
                try {
                    this.showType.add(ShowType.valueOf(showType));
                } catch (NoSuchElementException e) {
                    logger.warn("Invalid show type type: " + showType);
                }
            }
            return this;
        }

        public Builder withAggregation(Aggregation aggregation) {
            this.aggregation = aggregation;
            return this;
        }

        public EventViews build() {
            return new EventViews(
                    graph,
                    offset,
                    limit,
                    users,
                    ids,
                    entityTypes,
                    eventTypes,
                    from,
                    to,
                    showType,
                    aggregation
            );
        }
    }

    private EventViews(
            FramedGraph<?> graph,
            int offset,
            int limit,
            Collection<String> users,
            Collection<String> ids,
            Collection<EntityClass> entityTypes,
            Collection<EventTypes> eventTypes,
            Optional<String> from,
            Optional<String> to,
            Collection<ShowType> showType,
            Aggregation aggregation) {
        this.graph = graph;
        this.actionManager = new ActionManager(graph);
        this.manager = GraphManagerFactory.getInstance(graph);
        this.offset = offset;
        this.limit = limit;
        this.users = Sets.newHashSet(users);
        this.ids = Sets.newHashSet(ids);
        this.entityTypes = Sets.newEnumSet(entityTypes, EntityClass.class);
        this.eventTypes = Sets.newEnumSet(eventTypes, EventTypes.class);
        this.from = from;
        this.to = to;
        this.showType = Sets.newEnumSet(showType, ShowType.class);
        this.aggregation = aggregation;

    }

    public EventViews(FramedGraph<?> graph) {
        this(graph,
                -1,
                -1,
                Lists.<String>newArrayList(),
                Lists.<String>newArrayList(),
                Lists.<EntityClass>newArrayList(),
                Lists.<EventTypes>newArrayList(),
                Optional.<String>absent(),
                Optional.<String>absent(),
                Lists.<ShowType>newArrayList(),
                Aggregation.strict);
    }

    public Iterable<SystemEvent> list(Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(
                actionManager.getLatestGlobalEvents());

        // Add additional generic filters
        return setPipelineRange(applyAclFilter(filterEvents(pipe), accessor));
    }

    public Iterable<List<SystemEvent>> aggregate(Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(
                actionManager.getLatestGlobalEvents());

        // Add additional generic filters
        GremlinPipeline<SystemEvent, SystemEvent> aclFiltered =
                applyAclFilter(filterEvents(pipe), accessor);
        return setPipelineRange(aggregateFilter(aclFiltered));
    }

    /**
     * List items "interesting" for a user, optionally filtered by the `ShowType`
     * to items they watch or users they follow.
     */
    public Iterable<SystemEvent> listAsUser(UserProfile asUser, Accessor accessor) {
        GremlinPipeline<SystemEvent, SystemEvent> pipe = getPersonalisedEvents(asUser, accessor);
        return setPipelineRange(pipe);
    }

    /**
     * List events "interesting" for a user, with aggregation.
     */
    public Iterable<List<SystemEvent>> aggregateAsUser(UserProfile asUser, Accessor accessor) {
        GremlinPipeline<SystemEvent, SystemEvent> pipe = getPersonalisedEvents(asUser, accessor);
        return setPipelineRange(aggregateFilter(pipe));
    }

    /**
     * List an item's events.
     */
    public Iterable<SystemEvent> listForItem(AccessibleEntity item, Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(item.getHistory());
        // Add additional generic filters
        GremlinPipeline<SystemEvent, SystemEvent> acl = applyAclFilter(filterEvents(pipe), accessor);
        return setPipelineRange(acl);
    }

    /**
     * Aggregate an item's events.
     */
    public Iterable<List<SystemEvent>> aggregateForItem(AccessibleEntity item, Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(item.getHistory());
        // Add additional generic filters
        GremlinPipeline<SystemEvent, SystemEvent> acl = applyAclFilter(filterEvents(pipe), accessor);
        return setPipelineRange(aggregateFilter(acl));
    }

    /**
     * Aggregate a user's actions.
     *
     * @param byUser   the user
     * @param accessor the current accessor
     * @return an event stream
     */
    public Iterable<List<SystemEvent>> aggregateByUser(UserProfile byUser, Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(
                manager.cast(byUser, Actioner.class).getActions());

        // Add additional generic filters
        GremlinPipeline<SystemEvent, SystemEvent> acl = applyAclFilter(filterEvents(pipe), accessor);
        return setPipelineRange(aggregateFilter(acl));
    }

    /**
     * List a user's actions.
     *
     * @param byUser   the user
     * @param accessor the current accessor
     * @return an event stream
     */
    public Iterable<SystemEvent> listByUser(UserProfile byUser, Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(
                manager.cast(byUser, Actioner.class).getActions());

        // Add additional generic filters
        return setPipelineRange(applyAclFilter(filterEvents(pipe), accessor));
    }

    private GremlinPipeline<SystemEvent, SystemEvent> applyAclFilter(GremlinPipeline<SystemEvent, SystemEvent> pipe,
            Accessor asUser) {
        final PipeFunction<Vertex, Boolean> aclFilterTest = AclManager.getAclFilterFunction(asUser);

        // Filter items accessible to this asUser... hide the
        // event if any subjects or the scope are inaccessible
        // to the asUser.
        return pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
            @Override
            public Boolean compute(SystemEvent event) {
                Frame eventScope = event.getEventScope();
                if (eventScope != null && !aclFilterTest.compute(eventScope.asVertex())) {
                    return false;
                }
                for (AccessibleEntity e : event.getSubjects()) {
                    if (!aclFilterTest.compute(e.asVertex())) {
                        return false;
                    }
                }
                return true;
            }
        });
    }

    private GremlinPipeline<SystemEvent, SystemEvent> filterEvents(
            GremlinPipeline<SystemEvent, SystemEvent> pipe) {

        if (!eventTypes.isEmpty()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    return eventTypes.contains(event.getEventType());
                }
            });
        }

        if (!ids.isEmpty()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    for (AccessibleEntity e : event.getSubjects()) {
                        if (ids.contains(e.getId())) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        if (!entityTypes.isEmpty()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    for (AccessibleEntity e : event.getSubjects()) {
                        if (entityTypes.contains(manager.getEntityClass(e))) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        if (!users.isEmpty()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    Actioner actioner = event.getActioner();
                    return actioner != null && users.contains(actioner.getId());
                }
            });
        }

        // Add from/to filters (depends on timestamp strings comparing the right way!
        if (from.isPresent()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    String timestamp = event.getTimestamp();
                    return from.get().compareTo(timestamp) >= 0;
                }
            });
        }

        if (to.isPresent()) {
            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    String timestamp = event.getTimestamp();
                    return to.get().compareTo(timestamp) <= 0;
                }
            });
        }

        return pipe;
    }

    private GremlinPipeline<SystemEvent, SystemEvent> getPersonalisedEvents(UserProfile asUser, Accessor accessor) {
        // Add optional filters for event type, item type, and asUser...
        GremlinPipeline<SystemEvent,SystemEvent> pipe = new GremlinPipeline<>(
                actionManager.getLatestGlobalEvents());

        // Add additional generic filters
        pipe = filterEvents(pipe);

        // Filter out those we're not watching, or are actioned
        // by users we're not following...

        if (showType.contains(ShowType.watched)) {
            // Set IDs to items this asUser is watching...
            final List<String> watching = Lists.newArrayList();
            for (Watchable item : asUser.getWatching()) {
                watching.add(item.getId());
            }

            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    for (AccessibleEntity e : event.getSubjects()) {
                        if (watching.contains(e.getId())) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        if (showType.contains(ShowType.followed)) {
            final List<String> following = Lists.newArrayList();
            for (UserProfile other : asUser.getFollowing()) {
                following.add(other.getId());
            }

            pipe = pipe.filter(new PipeFunction<SystemEvent, Boolean>() {
                @Override
                public Boolean compute(SystemEvent event) {
                    Actioner actioner = event.getActioner();
                    return actioner != null && following.contains(actioner.getId());
                }
            });
        }

        return applyAclFilter(pipe, accessor);
    }

    public EventViews from(String from) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                entityTypes,
                eventTypes,
                Optional.fromNullable(from),
                to,
                showType,
                aggregation);
    }

    public EventViews to(String to) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                entityTypes,
                eventTypes,
                from,
                Optional.fromNullable(to),
                showType,
                aggregation);
    }

    public EventViews withIds(String... ids) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                Lists.newArrayList(ids),
                entityTypes,
                eventTypes,
                from,
                to,
                showType,
                aggregation);
    }

    public EventViews withRange(int offset, int limit) {
        return new EventViews(graph,
                offset,
                limit,
                Lists.newArrayList(users),
                ids,
                entityTypes,
                eventTypes,
                from,
                to,
                showType,
                aggregation);
    }

    public EventViews withUsers(String... users) {
        return new EventViews(graph,
                offset,
                limit,
                Lists.newArrayList(users),
                ids,
                entityTypes,
                eventTypes,
                from,
                to,
                showType,
                aggregation);
    }

    public EventViews withEntityClasses(EntityClass... entityTypes) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                Lists.newArrayList(entityTypes),
                eventTypes,
                from,
                to,
                showType,
                aggregation);
    }

    public EventViews withEventTypes(EventTypes... eventTypes) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                entityTypes,
                Lists.newArrayList(eventTypes),
                from,
                to,
                showType,
                aggregation);
    }

    public EventViews withShowType(ShowType... type) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                entityTypes,
                eventTypes,
                from,
                to,
                Lists.newArrayList(type),
                aggregation);
    }

    public EventViews withAggregation(Aggregation aggregation) {
        return new EventViews(graph,
                offset,
                limit,
                users,
                ids,
                entityTypes,
                eventTypes,
                from,
                to,
                showType,
                aggregation
        );
    }

    private GremlinPipeline<SystemEvent, List<SystemEvent>> aggregateFilter(GremlinPipeline<SystemEvent, SystemEvent> pipeline) {
        switch (aggregation) {
            case strict:
                return pipeline.add(new AggregatorPipe<>(strictAggregator));
            case user:
                return pipeline.add(new AggregatorPipe<>(userAggregator));
            default:
                // Default case: no aggregation, so simply wrap each event in a list
                return pipeline.transform(new PipeFunction<SystemEvent, List<SystemEvent>>() {
                    @Override
                    public List<SystemEvent> compute(SystemEvent event) {
                        return Lists.newArrayList(event);
                    }
                });
        }
    }

    private <E, S> GremlinPipeline<E, S> setPipelineRange(
            GremlinPipeline<E, S> filter) {
        int low = Math.max(0, offset);
        if (limit < 0) {
            // No way to skip a bunch of items in Gremlin without
            // applying an end range... I guess this will break if
            // we have more than 2147483647 items to traverse...
            return filter.range(low, Integer.MAX_VALUE); // No filtering
        } else if (limit == 0) {
            return new GremlinPipeline<>(Lists.newArrayList());
        } else {
            // NB: The high range is inclusive, oddly.
            return filter.range(low, low + (limit - 1));
        }
    }
}
