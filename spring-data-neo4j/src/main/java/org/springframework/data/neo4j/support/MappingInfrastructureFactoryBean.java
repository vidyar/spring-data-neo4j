/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.support;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.neo4j.annotation.QueryType;
import org.springframework.data.neo4j.conversion.ResultConverter;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.mapping.EntityInstantiator;
import org.springframework.data.neo4j.support.conversion.EntityResultConverter;
import org.springframework.data.neo4j.support.index.IndexProvider;
import org.springframework.data.neo4j.support.index.IndexProviderImpl;
import org.springframework.data.neo4j.support.mapping.EntityRemover;
import org.springframework.data.neo4j.support.mapping.EntityStateHandler;
import org.springframework.data.neo4j.support.mapping.EntityTools;
import org.springframework.data.neo4j.support.mapping.Neo4jEntityPersister;
import org.springframework.data.neo4j.support.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.support.node.EntityStateFactory;
import org.springframework.data.neo4j.support.node.NodeEntityInstantiator;
import org.springframework.data.neo4j.support.query.CypherQueryExecutor;
import org.springframework.data.neo4j.support.relationship.RelationshipEntityInstantiator;
import org.springframework.data.neo4j.support.typerepresentation.TypeRepresentationStrategies;
import org.springframework.data.neo4j.support.typerepresentation.TypeRepresentationStrategyFactory;
import org.springframework.transaction.PlatformTransactionManager;

import javax.validation.Validator;

import static org.springframework.data.neo4j.support.ParameterCheck.notNull;

/**
 * @author mh
 * @since 17.10.11
 */
public class MappingInfrastructureFactoryBean implements InitializingBean, FactoryBean<Infrastructure> {

    private Infrastructure infrastructure;

    public static Infrastructure createDirect(GraphDatabase graphDatabase, PlatformTransactionManager transactionManager) {
        notNull(graphDatabase, "graphDatabase");
        try {
            final MappingInfrastructureFactoryBean factoryBean = new MappingInfrastructureFactoryBean(graphDatabase, transactionManager);
            factoryBean.afterPropertiesSet();
            return factoryBean.getObject();
        } catch (Exception e) {
            throw new BeanCreationException("Error creating infrastructure", e);
        }
    }

    @Override
    public Infrastructure getObject() throws Exception {
        return infrastructure;
    }

    @Override
    public Class<?> getObjectType() {
        return Infrastructure.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private ConversionService conversionService;
    private Validator validator;
    private TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy;

    private TypeRepresentationStrategy<Relationship> relationshipTypeRepresentationStrategy;

    private TypeRepresentationStrategyFactory typeRepresentationStrategyFactory;

    private Neo4jMappingContext mappingContext;
    private CypherQueryExecutor cypherQueryExecutor;
    private EntityStateHandler entityStateHandler;
    private Neo4jEntityPersister entityPersister;
    private EntityStateFactory<Node> nodeEntityStateFactory;
    private EntityStateFactory<Relationship> relationshipEntityStateFactory;
    private EntityRemover entityRemover;
    private TypeRepresentationStrategies typeRepresentationStrategies;
    private EntityInstantiator<Relationship> relationshipEntityInstantiator;
    private EntityInstantiator<Node> nodeEntityInstantiator;
    private PlatformTransactionManager transactionManager;
    private ResultConverter resultConverter;
    private IndexProvider indexProvider;
    private GraphDatabaseService graphDatabaseService;
    private GraphDatabase graphDatabase;

    public MappingInfrastructureFactoryBean(GraphDatabase graphDatabase, PlatformTransactionManager transactionManager) {
        this.graphDatabase = graphDatabase;
        this.transactionManager = transactionManager;
    }

    public MappingInfrastructureFactoryBean() {
    }

    public void afterPropertiesSet() {
        if (this.mappingContext == null) this.mappingContext = new Neo4jMappingContext();
        if (this.graphDatabase == null) {
            this.graphDatabase = new DelegatingGraphDatabase(graphDatabaseService);
        }
        if (nodeEntityInstantiator == null) {
            nodeEntityInstantiator = new NodeEntityInstantiator(entityStateHandler);
        }
        if (relationshipEntityInstantiator == null) {
            relationshipEntityInstantiator = new RelationshipEntityInstantiator(entityStateHandler);
        }
        if (this.typeRepresentationStrategyFactory==null) {
            this.typeRepresentationStrategyFactory = new TypeRepresentationStrategyFactory(graphDatabase);
        }
        if (this.nodeTypeRepresentationStrategy == null) {
            this.nodeTypeRepresentationStrategy = typeRepresentationStrategyFactory.getNodeTypeRepresentationStrategy();
        }
        if (this.relationshipTypeRepresentationStrategy == null) {
            this.relationshipTypeRepresentationStrategy = typeRepresentationStrategyFactory.getRelationshipTypeRepresentationStrategy();
        }
        this.typeRepresentationStrategies = new TypeRepresentationStrategies(mappingContext, nodeTypeRepresentationStrategy, relationshipTypeRepresentationStrategy);

        final EntityStateHandler entityStateHandler = new EntityStateHandler(mappingContext, graphDatabase);
        EntityTools<Node> nodeEntityTools = new EntityTools<Node>(nodeTypeRepresentationStrategy, nodeEntityStateFactory, nodeEntityInstantiator,mappingContext);
        EntityTools<Relationship> relationshipEntityTools = new EntityTools<Relationship>(relationshipTypeRepresentationStrategy, relationshipEntityStateFactory, relationshipEntityInstantiator, mappingContext);
        this.entityPersister = new Neo4jEntityPersister(conversionService, nodeEntityTools, relationshipEntityTools, mappingContext, entityStateHandler);
        this.entityRemover = new EntityRemover(this.entityStateHandler, nodeTypeRepresentationStrategy, relationshipTypeRepresentationStrategy, graphDatabase);
        if (this.resultConverter==null) {
            this.resultConverter = new EntityResultConverter<Object, Object>(conversionService,entityPersister);
        }
        this.graphDatabase.setResultConverter(resultConverter);
        this.cypherQueryExecutor = new CypherQueryExecutor(graphDatabase.queryEngineFor(QueryType.Cypher, resultConverter));
        if (this.indexProvider == null) {
            this.indexProvider = new IndexProviderImpl(this.mappingContext, graphDatabase);
        }
        this.infrastructure = new MappingInfrastructure(nodeTypeRepresentationStrategy, validator, conversionService, graphDatabase, graphDatabaseService, indexProvider, resultConverter, transactionManager, typeRepresentationStrategies, entityRemover, entityPersister, entityStateHandler, cypherQueryExecutor, mappingContext, relationshipTypeRepresentationStrategy);
    }


    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setRelationshipEntityInstantiator(EntityInstantiator<Relationship> relationshipEntityInstantiator) {
        this.relationshipEntityInstantiator = relationshipEntityInstantiator;
    }

    public void setNodeEntityInstantiator(EntityInstantiator<Node> nodeEntityInstantiator) {
        this.nodeEntityInstantiator = nodeEntityInstantiator;
    }


    public void setEntityStateHandler(EntityStateHandler entityStateHandler) {
        this.entityStateHandler = entityStateHandler;
    }

    public void setNodeEntityStateFactory(EntityStateFactory<Node> nodeEntityStateFactory) {
        this.nodeEntityStateFactory = nodeEntityStateFactory;
    }

    public void setRelationshipEntityStateFactory(EntityStateFactory<Relationship> relationshipEntityStateFactory) {
        this.relationshipEntityStateFactory = relationshipEntityStateFactory;
    }


    public void setNodeTypeRepresentationStrategy(TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy) {
        this.nodeTypeRepresentationStrategy = nodeTypeRepresentationStrategy;
    }

    public void setRelationshipTypeRepresentationStrategy(TypeRepresentationStrategy<Relationship> relationshipTypeRepresentationStrategy) {
        this.relationshipTypeRepresentationStrategy = relationshipTypeRepresentationStrategy;
    }


    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }


    public void setValidator(Validator validatorFactory) {
        this.validator = validatorFactory;
    }

    public void setMappingContext(Neo4jMappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }



    public void setGraphDatabaseService(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    public void setGraphDatabase(GraphDatabase graphDatabase) {
        this.graphDatabase = graphDatabase;
    }

    public void setTypeRepresentationStrategyFactory(TypeRepresentationStrategyFactory typeRepresentationStrategyFactory) {
        this.typeRepresentationStrategyFactory = typeRepresentationStrategyFactory;
    }

    public void setIndexProvider(IndexProvider indexProvider) {
        this.indexProvider = indexProvider;
    }

    /*

    @Override
    public EntityStateHandler getEntityStateHandler() {
        return entityStateHandler;
    }

    @Override
    public TypeRepresentationStrategy<Node> getNodeTypeRepresentationStrategy() {
        return nodeTypeRepresentationStrategy;
    }
    @Override
    public TypeRepresentationStrategy<Relationship> getRelationshipTypeRepresentationStrategy() {
        return relationshipTypeRepresentationStrategy;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }
    @Override
    public Validator getValidator() {
        return validator;
    }
    @Override
    public GraphDatabaseService getGraphDatabaseService() {
        return graphDatabaseService;
    }

    @Override
    public GraphDatabase getGraphDatabase() {
        return graphDatabase;
    }

    @Override
    public ResultConverter getResultConverter() {
        return resultConverter;
    }

    @Override
    public EntityRemover getEntityRemover() {
        return entityRemover;
    }

    @Override
    public IndexProvider getIndexProvider() {
        return indexProvider;
    }

    @Override
    public Neo4jEntityPersister getEntityPersister() {
        return entityPersister;
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public TypeRepresentationStrategies getTypeRepresentationStrategies() {
        return typeRepresentationStrategies;
    }


    @Override
    public CypherQueryExecutor getCypherQueryExecutor() {
        return cypherQueryExecutor;
    }

    @Override
    public Neo4jMappingContext getMappingContext() {
        return mappingContext;
    }

    */
}
