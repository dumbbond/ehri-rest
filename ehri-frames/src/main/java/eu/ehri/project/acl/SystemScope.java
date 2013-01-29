package eu.ehri.project.acl;

import com.tinkerpop.blueprints.Vertex;

import eu.ehri.project.models.events.ItemEvent;
import eu.ehri.project.models.PermissionGrant;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.utils.EmptyIterable;

/**
 * Singleton class representing the system scope for
 * permissions and ID namespaces.
 * 
 * @author mike
 *
 */
public enum SystemScope implements PermissionScope, AccessibleEntity {
    
    INSTANCE;

    /**
     * Obtain the shared instance of SystemScope.
     * @return
     */
    public static PermissionScope getInstance() {
        return INSTANCE;
    }
    
    public static final String SYSTEM = "system";

    public String getIdentifier() {
        return SYSTEM;
    }

    public Vertex asVertex() {
        // TODO: Determine if there's a better approach to this.
        // Since PermissionScope can be implemented by several
        // types of node, comparing them by vertex is the only
        // reliable approach. Really, this operation should
        // throw an UnsupportedOperationException().
        return null;
    }

    public Iterable<PermissionGrant> getPermissionGrants() {
        return new EmptyIterable<PermissionGrant>();
    }

    public Iterable<Accessor> getAccessors() {
        return new EmptyIterable<Accessor>();
    }

    public void addAccessor(Accessor accessor) {
        throw new UnsupportedOperationException();
        
    }

    public void removeAccessor(Accessor accessor) {
        throw new UnsupportedOperationException();
        
    }

    public Iterable<PermissionGrant> getPermissionAssertions() {
        return new EmptyIterable<PermissionGrant>();
    }

    public PermissionScope getPermissionScope() {
        return null;
    }

    public void setPermissionScope(PermissionScope scope) {
        throw new UnsupportedOperationException();
    }

    public Iterable<PermissionScope> getPermissionScopes() {
        return new EmptyIterable<PermissionScope>();
    }

    public ItemEvent getLatestEvent() {
        // FIXME: Refactor frames hierarchy to make
        // AccessibleEntity less important.
        throw new UnsupportedOperationException();
    }

    public Iterable<ItemEvent> getHistory() {
        return new EmptyIterable<ItemEvent>();
    }
}
