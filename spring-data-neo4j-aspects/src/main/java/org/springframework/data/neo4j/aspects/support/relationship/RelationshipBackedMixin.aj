package org.springframework.data.neo4j.aspects.support.relationship;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.neo4j.annotation.RelationshipEntity;
import org.springframework.data.neo4j.aspects.core.NodeBacked;
import org.springframework.data.neo4j.aspects.core.RelationshipBacked;

/**
 * @author mh
 * @since 14.10.11
 */
public privileged aspect RelationshipBackedMixin {
    declare @type: RelationshipBacked+: @Configurable;
    declare @type: !@RelationshipEntity RelationshipBacked+: @RelationshipEntity;

    public void RelationshipBacked.remove() {
        Neo4jRelationshipBacking.aspectOf().graphDatabaseContext.removeRelationshipEntity(this);
    }

    public <R extends ManagedRelationshipEntity> R  RelationshipBacked.projectTo(Class<R> targetType) {
        return (R) Neo4jRelationshipBacking.aspectOf().graphDatabaseContext.projectTo(this, targetType);
    }

    public Long RelationshipBacked.getRelationshipId() {
        if (!hasPersistentState()) return null;
        return getPersistentState().getId();
    }
}
