/*
 * Copyright 2020 Data Archiving and Networked Services (an institute of
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

package eu.ehri.extension;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import eu.ehri.extension.base.*;
import eu.ehri.project.api.Api;
import eu.ehri.project.core.Tx;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.exporters.eac.Eac2010Exporter;
import eu.ehri.project.exporters.eac.EacExporter;
import eu.ehri.project.importers.ImportCallback;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.json.BatchOperations;
import eu.ehri.project.models.HistoricalAgent;
import eu.ehri.project.models.base.Accessible;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.cvoc.AuthoritativeItem;
import eu.ehri.project.models.cvoc.AuthoritativeSet;
import eu.ehri.project.persistence.Bundle;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Provides a web service interface for the AuthoritativeSet items. model.
 * Authoritative Sets are containers for Historical Agents
 * (authority files.)
 */
@Path(AbstractResource.RESOURCE_ENDPOINT_PREFIX + "/" + Entities.AUTHORITATIVE_SET)
public class AuthoritativeSetResource extends
        AbstractAccessibleResource<AuthoritativeSet>
        implements GetResource, ListResource, DeleteResource, CreateResource, UpdateResource, ParentResource {

    public AuthoritativeSetResource(@Context GraphDatabaseService database) {
        super(database, AuthoritativeSet.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response get(@PathParam("id") String id) throws ItemNotFound {
        return getItem(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response list() {
        return listItems();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}/list")
    @Override
    public Response listChildren(
            @PathParam("id") String id,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all) throws ItemNotFound {

        try (final Tx tx = beginTx()) {
            AuthoritativeSet set = api().detail(id, cls);
            Response response = streamingPage(() ->
                    getQuery().page(set.getAuthoritativeItems(), AuthoritativeItem.class));
            tx.success();
            return response;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response create(Bundle bundle,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, DeserializationError {
        try (Tx tx = beginTx()) {
            Response response = createItem(bundle, accessors);
            tx.success();
            return response;
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response update(@PathParam("id") String id, Bundle bundle)
            throws PermissionDenied, ValidationError,
            DeserializationError, ItemNotFound {
        try (Tx tx = beginTx()) {
            Response response = updateItem(id, bundle);
            tx.success();
            return response;
        }
    }

    @DELETE
    @Path("{id:[^/]+}")
    @Override
    public void delete(@PathParam("id") String id)
            throws PermissionDenied, ItemNotFound, ValidationError {
        try (Tx tx = beginTx()) {
            deleteItem(id);
            tx.success();
        }
    }

    @DELETE
    @Path("{id:[^/]+}/all")
    public Response deleteAllAuthoritativeSetHistoricalAgents(
            @PathParam("id") String id)
            throws ItemNotFound, PermissionDenied {
        try (Tx tx = beginTx()) {
            Api api = api();
            AuthoritativeSet set = api.detail(id, cls);
            Iterable<AuthoritativeItem> agents = set.getAuthoritativeItems();
            Api scopedApi = api.withScope(set);
            for (AuthoritativeItem agent : agents) {
                scopedApi.delete(agent.getId());
            }
            tx.success();
            return Response.status(Status.OK).build();
        } catch (ValidationError | SerializationError e) {
            throw new RuntimeException(e);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response createChild(@PathParam("id") String id,
            Bundle bundle, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError,
            DeserializationError, ItemNotFound {
        try (Tx tx = beginTx()) {
            final AuthoritativeSet set = api().detail(id, cls);
            Response item = createItem(bundle, accessors, agent -> {
                set.addItem(agent);
                agent.setPermissionScope(set);
            }, api().withScope(set), HistoricalAgent.class);
            tx.success();
            return item;
        }
    }

    /**
     * Add items to a set via serialised data.
     *
     * @param id       the set ID
     * @param data     a list of serialised items
     * @return an import log
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}/list")
    public ImportLog addChildren(
            @PathParam("id") String id,
            @QueryParam(TOLERANT_PARAM) @DefaultValue("false") boolean tolerant,
            @QueryParam(VERSION_PARAM) @DefaultValue("true") boolean version,
            @QueryParam(COMMIT_PARAM) @DefaultValue("false") boolean commit,
            InputStream data) throws ItemNotFound, DeserializationError, ValidationError {
        try (final Tx tx = beginTx()) {
            Actioner user = getCurrentActioner();
            AuthoritativeSet set = api().detail(id, cls);
            ImportCallback cb = mutation -> {
                Accessible accessible = mutation.getNode();
                if (!Entities.HISTORICAL_AGENT.equals(accessible.getType())) {
                    throw new RuntimeException("Bundle is not an historical agent: " + accessible.getId());
                }
                accessible.setPermissionScope(set);
                set.addItem(accessible.as(HistoricalAgent.class));
            };
            ImportLog log = new BatchOperations(graph, set, version, tolerant,
                    Lists.newArrayList(cb)).batchImport(data, user, getLogMessage());
            if (commit) {
                logger.debug("Committing batch ingest transaction...");
                tx.success();
            }
            return log;
        }
    }


    /**
     * Export the given set's historical agents as EAC streamed
     * in a ZIP file.
     *
     * @param id   the set ID
     * @param lang a three-letter ISO639-2 code
     * @return a zip containing the set's historical agents as EAC
     */
    @GET
    @Path("{id:[^/]+}/eac")
    @Produces("application/zip")
    public Response exportEag(@PathParam("id") String id,
            final @QueryParam(LANG_PARAM) @DefaultValue(DEFAULT_LANG) String lang)
            throws IOException, ItemNotFound {
        try (final Tx tx = beginTx()) {
            final AuthoritativeSet set = api().detail(id, cls);
            final EacExporter eacExporter = new Eac2010Exporter(api());
            Iterable<HistoricalAgent> agents = Iterables
                    .transform(set.getAuthoritativeItems(), a -> a.as(HistoricalAgent.class));
            Response response = exportItemsAsZip(eacExporter, agents, lang);
            tx.success();
            return response;
        }
    }
}
