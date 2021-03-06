/*
 *  Copyright (c) 2017 Otávio Santana and others
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   and Apache License v2.0 which accompanies this distribution.
 *   The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *   and the Apache License v2.0 is available at http://www.opensource.org/licenses/apache2.0.php.
 *
 *   You may elect to redistribute this code under either of these licenses.
 *
 *   Contributors:
 *
 *   Otavio Santana
 */
package org.jnosql.diana.orientdb.document;

import org.jnosql.diana.api.document.Document;
import org.jnosql.diana.api.document.DocumentCollectionManager;
import org.jnosql.diana.api.document.DocumentCollectionManagerAsync;
import org.jnosql.diana.api.document.DocumentDeleteQuery;
import org.jnosql.diana.api.document.DocumentEntity;
import org.jnosql.diana.api.document.DocumentQuery;
import org.jnosql.diana.api.document.Documents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static java.util.logging.Level.FINEST;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.jnosql.diana.api.document.query.DocumentQueryBuilder.delete;
import static org.jnosql.diana.api.document.query.DocumentQueryBuilder.select;
import static org.jnosql.diana.orientdb.document.DocumentConfigurationUtils.getAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class OrientDBDocumentCollectionManagerAsyncTest {

    public static final String COLLECTION_NAME = "person";

    private DocumentCollectionManagerAsync entityManagerAsync;

    private DocumentCollectionManager entityManager;

    private static final Logger LOGGER = Logger.getLogger(OrientDBDocumentCollectionManagerTest.class.getName());

    @BeforeEach
    public void setUp() {
        entityManagerAsync = getAsync().getAsync("database");
        entityManager = DocumentConfigurationUtils.get().get("database");
        DocumentEntity documentEntity = getEntity();
        Document id = documentEntity.find("name").get();
        DocumentDeleteQuery deleteQuery = delete().from(COLLECTION_NAME).where(id.getName()).eq(id.get()).build();

        try {
            entityManagerAsync.delete(deleteQuery);
        } catch (Exception e) {
            LOGGER.log(FINEST, "error on OrientDB setup", e);
        }
    }


    @Test
    public void shouldSaveAsync() throws InterruptedException {
        AtomicReference<DocumentEntity> entityAtomic = new AtomicReference<>();
        entityManagerAsync.insert(getEntity(), entityAtomic::set);
        await().until(entityAtomic::get, notNullValue(DocumentEntity.class));

        DocumentEntity entity = entityAtomic.get();
        Document id = entity.find("name").get();

        DocumentQuery query =  select().from(COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        List<DocumentEntity> entities = entityManager.select(query);
        assertFalse(entities.isEmpty());

    }

    @Test
    public void shouldUpdateAsync() {
        DocumentEntity entity = getEntity();
        entityManager.insert(entity);
        Document newField = Documents.of("newField", "10");
        entity.add(newField);

        entityManagerAsync.update(entity);
    }

    @Test
    public void shouldUpdateAsyncWithCallback() {
        final String NEW_FIELD_NAME = "newField2";
        final String NEW_FIELD_VALUE = "55";

        DocumentEntity entity = entityManager.insert(getEntity());
        Document newField = Documents.of(NEW_FIELD_NAME, NEW_FIELD_VALUE);
        entity.add(newField);

        AtomicBoolean condition = new AtomicBoolean(false);
        entityManagerAsync.update(entity, c -> condition.set(true));
        await().untilTrue(condition);

        Optional<Document> idDocument = entity.find(OrientDBConverter.RID_FIELD);
        DocumentQuery query = select().from(entity.getName())
                .where(idDocument.get().getName()).eq(idDocument.get().get())
                .build();
        Optional<DocumentEntity> entityUpdated = entityManager.singleResult(query);

        assertTrue(entityUpdated.isPresent());
        assertEquals(entityUpdated.get().find(NEW_FIELD_NAME).get(), newField);
    }

    @Test
    public void shouldRemoveEntityAsync() throws InterruptedException {
        DocumentEntity documentEntity = entityManager.insert(getEntity());
        Document id = documentEntity.find("name").get();

        DocumentQuery query =  select().from(COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        DocumentDeleteQuery deleteQuery = delete().from(COLLECTION_NAME).where(id.getName()).eq(id.get()).build();

        AtomicBoolean condition = new AtomicBoolean(false);
        entityManagerAsync.delete(deleteQuery, c -> condition.set(true));
        await().untilTrue(condition);
        assertTrue(entityManager.select(query).isEmpty());
    }

    private DocumentEntity getEntity() {
        DocumentEntity entity = DocumentEntity.of(COLLECTION_NAME);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Poliana");
        map.put("city", "Salvador");
        List<Document> documents = Documents.of(map);
        documents.forEach(entity::add);
        return entity;
    }
}