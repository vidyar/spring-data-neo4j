package org.springframework.data.neo4j.aspects.support.node;

import org.neo4j.graphdb.traversal.Traverser;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.neo4j.aspects.core.NodeBacked;
import org.springframework.data.neo4j.aspects.support.relationship.ManagedRelationshipEntity;
import org.springframework.data.neo4j.support.RelationshipResult;
import org.springframework.data.neo4j.support.path.EntityPathPathIterableWrapper;
import org.springframework.data.neo4j.support.query.CypherQueryExecutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.FieldSignature;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;

import org.springframework.data.neo4j.support.ManagedEntity;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.annotation.RelationshipEntity;
import org.springframework.data.neo4j.annotation.RelatedTo;
import org.springframework.data.neo4j.annotation.GraphProperty;
import org.springframework.data.neo4j.annotation.GraphId;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.annotation.RelatedToVia;
import org.springframework.data.neo4j.annotation.GraphTraversal;

import org.springframework.data.neo4j.aspects.core.NodeBacked;
import org.springframework.data.neo4j.aspects.core.RelationshipBacked;
import org.springframework.data.neo4j.support.EntityStateHandler;
import org.springframework.data.neo4j.support.RelationshipResult;
import org.springframework.data.neo4j.support.node.NodeEntityStateFactory;
import org.springframework.data.neo4j.support.DoReturn;
import org.springframework.data.neo4j.core.EntityPath;
import org.springframework.data.neo4j.core.EntityState;
import org.springframework.data.neo4j.support.GraphDatabaseContext;

import org.springframework.data.neo4j.support.path.EntityPathPathIterableWrapper;
import org.springframework.data.neo4j.support.query.CypherQueryExecutor;

import javax.persistence.Transient;
import javax.persistence.Entity;

import java.lang.reflect.Field;
import java.util.Map;

import static org.springframework.data.neo4j.support.DoReturn.unwrap;

/**
 * @author mh
 * @since 14.10.11
 */
public privileged aspect NodeBackedMixin {

     declare @type: NodeBacked+: @Configurable;
     declare @type: !@NodeEntity NodeBacked+: @NodeEntity;

    public <T extends ManagedNodeEntity> T NodeBacked.projectTo(Class<T> targetType) {
        return (T)graphDatabaseContext().projectTo( this, targetType);
    }

	public Relationship NodeBacked.relateTo(ManagedNodeEntity target, String type) {
        return this.relateTo(target, type, false);
    }
    public Relationship NodeBacked.relateTo(ManagedNodeEntity target, String type, boolean allowDuplicates) {
        final RelationshipResult result = Neo4jNodeBacking.entityStateHandler().relateTo(this, target, type, allowDuplicates);
        return result.relationship;
	}

    public Relationship NodeBacked.getRelationshipTo(ManagedNodeEntity target, String type) {
        return graphDatabaseContext().getRelationshipTo(this,target,null,type);
    }

	public Long NodeBacked.getNodeId() {
        if (!hasPersistentState()) return null;
		return getPersistentState().getId();
	}

    public  <T> Iterable<T> NodeBacked.findAllByTraversal(final Class<T> targetType, TraversalDescription traversalDescription) {
        if (!hasPersistentState()) throw new IllegalStateException("No node attached to " + this);
        final Traverser traverser = traversalDescription.traverse(this.getPersistentState());
        return graphDatabaseContext().convertResultsTo(traverser, targetType);
    }

    public  <T> Iterable<T> NodeBacked.findAllByQuery(final String query, final Class<T> targetType, Map<String,Object> params) {
        final CypherQueryExecutor executor = new CypherQueryExecutor(graphDatabaseContext());
        return executor.query(query, targetType,params);
    }

    public  Iterable<Map<String,Object>> NodeBacked.findAllByQuery(final String query,Map<String,Object> params) {
        final CypherQueryExecutor executor = new CypherQueryExecutor(graphDatabaseContext());
        return executor.queryForList(query,params);
    }

    public  <T> T NodeBacked.findByQuery(final String query, final Class<T> targetType,Map<String,Object> params) {
        final CypherQueryExecutor executor = new CypherQueryExecutor(graphDatabaseContext());
        return executor.queryForObject(query, targetType,params);
    }

    public <S extends ManagedNodeEntity, E extends ManagedNodeEntity> Iterable<EntityPath<S,E>> NodeBacked.findAllPathsByTraversal(TraversalDescription traversalDescription) {
        if (!hasPersistentState()) throw new IllegalStateException("No node attached to " + this);
        final Traverser traverser = traversalDescription.traverse(this.getPersistentState());
        return new EntityPathPathIterableWrapper<S, E>(traverser, graphDatabaseContext());
    }

    public <R extends ManagedRelationshipEntity, N extends ManagedNodeEntity> R NodeBacked.relateTo(N target, Class<R> relationshipClass, String relationshipType) {
        return graphDatabaseContext().relateTo(this, target, relationshipClass, relationshipType, false);
    }
    public <R extends ManagedRelationshipEntity, N extends ManagedNodeEntity> R NodeBacked.relateTo(N target, Class<R> relationshipClass, String relationshipType, boolean allowDuplicates) {
        return graphDatabaseContext().relateTo(this,target,relationshipClass, relationshipType,allowDuplicates);
    }

    public void NodeBacked.remove() {
        graphDatabaseContext().removeNodeEntity(this);
    }

    public void NodeBacked.removeRelationshipTo(ManagedNodeEntity target, String relationshipType) {
        graphDatabaseContext().removeRelationshipTo(this,target,relationshipType);
    }

    public <R extends ManagedRelationshipEntity> R NodeBacked.getRelationshipTo( ManagedNodeEntity target, Class<R> relationshipClass, String type) {
        return (R)graphDatabaseContext().getRelationshipTo(this,target,relationshipClass,type);
    }

    public static GraphDatabaseContext graphDatabaseContext() {
        return Neo4jNodeBacking.aspectOf().graphDatabaseContext;
    }
}
