package eu.ehri.extension;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import eu.ehri.project.exceptions.*;
import eu.ehri.project.views.AclViews;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Group;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.views.Query;

/**
 * Provides a RESTfull interface for the Group class.
 */
@Path(Entities.GROUP)
public class GroupResource extends AbstractAccessibleEntityResource<Group> {

    public GroupResource(@Context GraphDatabaseService database) {
        super(database, Group.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response getGroup(@PathParam("id") String id) throws ItemNotFound,
            AccessDenied, BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    public StreamingOutput listGroups(
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return list(offset, limit, order, filters);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/count")
    public Response countVocabularies(@QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return count(filters);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/page")
    public StreamingOutput pageGroups(
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return page(offset, limit, order, filters);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroup(String json,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        return create(json, accessors);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGroup(String json) throws AccessDenied, PermissionDenied,
            IntegrityError, ValidationError, DeserializationError,
            ItemNotFound, BadRequester {
        return update(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response updateGroup(@PathParam("id") String id, String json)
            throws AccessDenied, PermissionDenied, IntegrityError, ValidationError,
            DeserializationError, ItemNotFound, BadRequester {
        return update(id, json);
    }

    /**
     * Add an accessor to a group.
     *
     * @param id
     * @param atype
     * @param aid
     * @return
     * @throws PermissionDenied
     * @throws ItemNotFound
     * @throws BadRequester
     */
    @POST
    @Path("/{id:[^/]+}/{aid:.+}")
    public Response addMember(@PathParam("id") String id,
            @PathParam("atype") String atype, @PathParam("aid") String aid)
            throws PermissionDenied, ItemNotFound, BadRequester {
        Group group = manager.getFrame(id, EntityClass.GROUP, Group.class);
        Accessor accessor = manager.getFrame(aid, Accessor.class);
        new AclViews(graph).addAccessorToGroup(group, accessor, getRequesterUserProfile());
        return Response.status(Status.OK).build();
    }

    /**
     * Remove an accessor from a group.
     *
     * @param id
     * @param aid
     * @param aid
     * @return
     * @throws PermissionDenied
     * @throws ItemNotFound
     * @throws BadRequester
     */
    @DELETE
    @Path("/{id:[^/]+}/{aid:.+}")
    public Response removeMember(@PathParam("id") String id,
            @PathParam("aid") String aid) throws PermissionDenied,
            ItemNotFound, BadRequester {

        Group group = manager.getFrame(id, EntityClass.GROUP, Group.class);
        Accessor accessor = manager.getFrame(aid, Accessor.class);
        new AclViews(graph).removeAccessorFromGroup(group, accessor, getRequesterUserProfile());
        return Response.status(Status.OK).build();
    }

    /**
     * list members of the specified group; 
     * UserProfiles and sub-Groups (direct descendants)
     * 
     * @param id
     * @param offset
     * @param limit
     * @param order
     * @param filters
     * @return
     * @throws ItemNotFound
     * @throws BadRequester
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:[^/]+}/list")
    public StreamingOutput listGroupMembers(
    		@PathParam("id") String id,
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,            
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        Group group = manager.getFrame(id, EntityClass.GROUP, Group.class);
        // TODO list all users of the group
        // get them from the RelationShip
        // Iterable<Accessor> members = group.getMembers();
        // use offset to skip is not efficient... but what else to do
        // better query and add a filter to reduce for the specified group
        Query<AccessibleEntity> userQuerier = new Query<AccessibleEntity>(graph, AccessibleEntity.class);
        Query<AccessibleEntity> query = userQuerier.setOffset(offset).setLimit(limit)
                .orderBy(order).filter(filters);
        return streamingList(query.list(group.getMembersAsEntities(), getRequesterUserProfile()));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}/count")
    public Response countGroupMembers(
            @PathParam("id") String id,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester, AccessDenied {
        Accessor user = getRequesterUserProfile();
        Group group = views.detail(manager.getFrame(id, cls), user);
        Query<AccessibleEntity> query = new Query<AccessibleEntity>(graph, AccessibleEntity.class)
                .filter(filters);
        return Response.ok((query.count(group.getMembersAsEntities(), user))
                .toString().getBytes()).build();
    }

    /**
     * Delete a group with the given identifier string.
     *
     * @param id
     * @return
     * @throws PermissionDenied
     * @throws ItemNotFound
     * @throws ValidationError
     * @throws BadRequester
     */
    @DELETE
    @Path("/{id:.+}")
    public Response deleteGroup(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        return delete(id);
    }
}
