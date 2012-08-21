package eu.ehri.project.acl;

import eu.ehri.project.models.*;
import eu.ehri.project.relationships.*;


public class EntityAccessFactory {
    public EntityAccessFactory() {}

    public Access buildReadWrite(Entity entity, Accessor accessor) {
        return new EntityAccess(true, true, entity, accessor);
    }

    public Access buildReadOnly(Entity entity, Accessor accessor) {
        return new EntityAccess(true, false, entity, accessor);
    }

    public Access buildNoAccess(Entity entity, Accessor accessor) {
        return new EntityAccess(false, false, entity, accessor);
    }
}

