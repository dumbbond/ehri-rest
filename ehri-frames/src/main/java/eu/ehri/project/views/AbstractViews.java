package eu.ehri.project.views;

import java.util.NoSuchElementException;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.AnonymousAccessor;
import eu.ehri.project.acl.PermissionTypes;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.models.ContentType;
import eu.ehri.project.models.Permission;
import eu.ehri.project.models.PermissionGrant;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.utils.ClassUtils;
import eu.ehri.project.persistance.Converter;

abstract class AbstractViews<E extends AccessibleEntity> {

    protected final FramedGraph<Neo4jGraph> graph;
    protected final Class<E> cls;
    protected final Converter converter = new Converter();
    protected final AclManager acl;
    /**
     * Default scope for Permission operations is the system,
     * but this can be overridden.
     */
    protected AccessibleEntity scope = new SystemScope();

    /**
     * @param graph
     * @param cls
     */
    public AbstractViews(FramedGraph<Neo4jGraph> graph, Class<E> cls) {
        this.graph = graph;
        this.cls = cls;
        this.acl = new AclManager(graph);
    }

    /**
     * Check permissions for a given type.
     * 
     * @throws PermissionDenied
     */
    protected void checkPermission(Long user, String permissionId)
            throws PermissionDenied {
        Accessor accessor = getAccessor(user);
        // If we're admin, the answer is always "no problem"!
        if (!acl.isAdmin(accessor)) {
            ContentType contentType = getContentType(ClassUtils
                    .getEntityType(cls));
            Permission permission = getPermission(permissionId);
            Iterable<PermissionGrant> perms = acl.getPermissions(accessor,
                    contentType, permission);
            boolean found = false;
            for (PermissionGrant perm : perms) {
                // If the permission has unscoped rights, the user is
                // good to do whatever they want to do here.
                Iterable<PermissionScope> scopes = perm.getScopes();
                if (!scopes.iterator().hasNext()) {
                    found = true;
                    break;
                }
                // Otherwise, verify that the given scope is included.
                for (PermissionScope s : scopes) {
                    if (s.asVertex().equals(scope.asVertex())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new PermissionDenied(String.format(
                        "Permission '%s' denied with scope: '%s'",
                        permission.getIdentifier(), scope));
            }
        }
    }

    /**
     * Check permissions for a given entity.
     * 
     * @throws PermissionDenied
     */
    protected void checkEntityPermission(AccessibleEntity entity, Long user,
            String permissionId) throws PermissionDenied {

        // TODO: Determine behaviour for granular item-level
        // attributes.
        try {
            checkPermission(user, permissionId);
        } catch (PermissionDenied e) {
            Accessor accessor = getAccessor(user);
            Iterable<PermissionGrant> perms = acl.getPermissions(accessor,
                    entity, getPermission(permissionId));
            // Scopes do not apply to entity-level perms...
            if (!perms.iterator().hasNext())
                throw new PermissionDenied(accessor, entity);
        }

    }

    /**
     * Ensure an item is readable by the given user
     * 
     * @param entity
     * @param user
     * @throws PermissionDenied
     */
    protected void checkReadAccess(AccessibleEntity entity, Long user)
            throws PermissionDenied {
        Accessor accessor = getAccessor(user);
        if (!acl.getAccessControl(entity, accessor))
            throw new PermissionDenied(accessor, entity);
    }

    /**
     * Ensure an item is writable by the given user
     * 
     * @param entity
     * @param user
     * @throws PermissionDenied
     */
    protected void checkWriteAccess(AccessibleEntity entity, Long user)
            throws PermissionDenied {
        checkEntityPermission(entity, user, PermissionTypes.UPDATE);
    }

    /**
     * Get the access with the given id, or the special anonymous access
     * otherwise.
     * 
     * @param id
     * @return
     */
    protected Accessor getAccessor(Long id) {
        if (id == null)
            return new AnonymousAccessor();
        // FIXME: Ensure this item really is an accessor!
        return graph.frame(graph.getVertex(id), Accessor.class);
    }

    /**
     * Get the content type with the given id.
     * 
     * @param typeName
     * @return
     */
    public ContentType getContentType(String typeName) {
        try {
            return graph
                    .getVertices(AccessibleEntity.IDENTIFIER_KEY, typeName,
                            ContentType.class).iterator().next();
        } catch (NoSuchElementException e) {
            throw new RuntimeException(String.format(
                    "No content type node found for type: '%s'", typeName), e);
        }
    }

    /**
     * Get the permission with the given string.
     * 
     * @param permissionId
     * @return
     */
    public Permission getPermission(String permissionId) {
        try {
            return graph
                    .getVertices(AccessibleEntity.IDENTIFIER_KEY, permissionId,
                            Permission.class).iterator().next();
        } catch (NoSuchElementException e) {
            throw new RuntimeException(String.format(
                    "No permission found for name: '%s'", permissionId), e);
        }
    }

    /**
     * Set the scope under which ACL and permission operations will take place.
     * This is, for example, an Agent instance, where the objects being
     * manipulated are DocumentaryUnits. The given scope is used to compare
     * against the scope relation on PermissionGrants.
     * 
     * @param scope
     */
    public void setScope(AccessibleEntity scope) {
        this.scope = scope;
    }
}
