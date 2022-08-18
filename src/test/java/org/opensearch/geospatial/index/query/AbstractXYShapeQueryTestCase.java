/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.query;

import java.util.Map;

import org.opensearch.common.settings.Settings;
import org.opensearch.geospatial.GeospatialRestTestCase;

public abstract class AbstractXYShapeQueryTestCase extends GeospatialRestTestCase {
    public void queryTestUsingNullShape(String indexName, String fieldName, String contentType) throws Exception {
        createIndex(indexName, Settings.EMPTY, Map.of(fieldName, contentType));
        String body = buildContentAsString(builder -> builder.field(fieldName, (String) null));
        String docID = indexDocument(indexName, body);

        final Map<String, Object> document = getDocument(docID, indexName);
        assertTrue("failed to index document with type", document.containsKey(fieldName));
        assertNull("failed to accept null value", document.get(fieldName));

        deleteIndex(indexName);
    }

}
