/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.query.xypoint;

import static org.hamcrest.Matchers.equalTo;
import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.geospatial.GeospatialTestHelper.randomLowerCaseString;
import static org.opensearch.index.query.AbstractGeometryQueryBuilder.DEFAULT_SHAPE_FIELD_NAME;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertSearchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.ResponseException;
import org.opensearch.common.geo.GeoJson;
import org.opensearch.common.geo.ShapeRelation;
import org.opensearch.common.settings.Settings;
import org.opensearch.geometry.LinearRing;
import org.opensearch.geometry.MultiPolygon;
import org.opensearch.geometry.Polygon;
import org.opensearch.geometry.Rectangle;
import org.opensearch.geospatial.GeospatialRestTestCase;
import org.opensearch.geospatial.index.mapper.xypoint.XYPointFieldMapper;
import org.opensearch.geospatial.index.mapper.xyshape.XYShapeFieldMapper;
import org.opensearch.geospatial.index.query.QueryTestHelper;
import org.opensearch.geospatial.index.query.xyshape.XYShapeQueryBuilder;
import org.opensearch.search.SearchHit;

public class XYPointQueryIT extends GeospatialRestTestCase {
    private static final String INDEXED_SHAPE_FIELD = "indexed_shape";
    private static final String SHAPE_INDEX_FIELD = "index";
    private static final String SHAPE_ID_FIELD = "id";
    private static final String SHAPE_INDEX_PATH_FIELD = "path";
    private static final String SHAPE_RELATION = "relation";
    private String indexName;
    private String xyPointFieldName;
    private QueryTestHelper queryTestHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        indexName = randomLowerCaseString();
        xyPointFieldName = randomLowerCaseString();
        queryTestHelper = new QueryTestHelper();
    }

    public void testNullShape() throws Exception {
        queryTestHelper.queryTestUsingNullShape(indexName, xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE);
    }

    public void testIndexPointsFilterRectangleWithIntersectsRelation() throws Exception {
        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));
        final String firstDocumentID = queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-30 -30)");
        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-45 -50)");

        Rectangle rectangle = new Rectangle(-45, 45, 45, -45);
        final SearchResponse searchResponse = queryTestHelper.searchUsingIntersectsRelation(indexName, xyPointFieldName, rectangle);
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        MatcherAssert.assertThat(searchResponse.getHits().getAt(0).getId(), equalTo(firstDocumentID));

        deleteIndex(indexName);
    }

    public void testIndexPointsFilterRectangleWithUnsupportedRelation() throws Exception {
        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));

        final String firstDocument = buildDocumentWithWKT(xyPointFieldName, "POINT(-30 -30)");
        indexDocument(indexName, firstDocument);

        Rectangle rectangle = new Rectangle(-45, 45, 45, -45);
        String searchEntity = buildSearchBodyAsString(builder -> {
            builder.field(DEFAULT_SHAPE_FIELD_NAME);
            GeoJson.toXContent(rectangle, builder, EMPTY_PARAMS);
            builder.field(SHAPE_RELATION, ShapeRelation.CONTAINS.getRelationName());
        }, XYShapeQueryBuilder.NAME, xyPointFieldName);

        ResponseException exception = expectThrows(ResponseException.class, () -> searchIndex(indexName, searchEntity));
        assertTrue(exception.getMessage().contains("CONTAINS query relation not supported"));

        deleteIndex(indexName);
    }

    public void testIndexPointsIndexedRectangleMatches() throws Exception {

        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));
        // Will index two points and search with envelope that will intersect only one point
        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-30 -30)");
        final String secondDocumentID = queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-45 -50)");

        // create an index to insert shape
        String indexedShapeIndex = randomLowerCaseString();
        String indexedShapePath = randomLowerCaseString();
        createIndex(indexedShapeIndex, Settings.EMPTY, Map.of(indexedShapePath, XYShapeFieldMapper.CONTENT_TYPE));

        final String shapeDocID = queryTestHelper.indexDocumentUsingWKT(indexedShapeIndex, indexedShapePath, "BBOX(-50, -40, -45, -55)");

        final SearchResponse searchResponse = queryTestHelper.searchUsingIndexedShapeIndex(
            indexName,
            indexedShapeIndex,
            indexedShapePath,
            shapeDocID,
            xyPointFieldName
        );
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        MatcherAssert.assertThat(searchResponse.getHits().getAt(0).getId(), equalTo(secondDocumentID));

        deleteIndex(indexName);
        deleteIndex(indexedShapeIndex);
    }

    public void testIndexPointsPolygon() throws Exception {
        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));

        final String firstDocumentID = queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-30 -30)");
        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-45 -50)");

        double[] x = new double[] { -35, -35, -25, -25, -35 };
        double[] y = new double[] { -35, -25, -25, -35, -35 };
        LinearRing ring = new LinearRing(x, y);
        Polygon polygon = new Polygon(ring);

        final SearchResponse searchResponse = queryTestHelper.searchUsingIntersectsRelation(indexName, xyPointFieldName, polygon);
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        MatcherAssert.assertThat(searchResponse.getHits().getAt(0).getId(), equalTo(firstDocumentID));

        deleteIndex(indexName);
    }

    public void testIndexPointsMultiPolygon() throws Exception {
        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));

        final String firstDocumentID = queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-30 -30)");
        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-40 -40)");
        final String thirdDocumentId = queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-50 -50)");

        LinearRing ring1 = new LinearRing(new double[] { -35, -35, -25, -25, -35 }, new double[] { -35, -25, -25, -35, -35 });
        Polygon polygon1 = new Polygon(ring1);

        LinearRing ring2 = new LinearRing(new double[] { -55, -55, -45, -45, -55 }, new double[] { -55, -45, -45, -55, -55 });
        Polygon polygon2 = new Polygon(ring2);

        MultiPolygon multiPolygon = new MultiPolygon(List.of(polygon1, polygon2));
        List<String> expectedDocIDs = List.of(firstDocumentID, thirdDocumentId);

        final SearchResponse searchResponse = queryTestHelper.searchUsingIntersectsRelation(indexName, xyPointFieldName, multiPolygon);
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, expectedDocIDs.size());
        List<String> actualDocIDS = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            actualDocIDS.add(hit.getId());
        }
        MatcherAssert.assertThat(expectedDocIDs, Matchers.containsInAnyOrder(actualDocIDS.toArray()));
    }

    public void testIndexPointsIndexedRectangleNoMatch() throws Exception {

        createIndex(indexName, Settings.EMPTY, Map.of(xyPointFieldName, XYPointFieldMapper.CONTENT_TYPE));

        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-30 -30)");
        queryTestHelper.indexDocumentUsingWKT(indexName, xyPointFieldName, "POINT(-45 -50)");

        // create an index to insert shape
        String indexedShapeIndex = randomLowerCaseString();
        String indexedShapePath = randomLowerCaseString();
        createIndex(indexedShapeIndex, Settings.EMPTY, Map.of(indexedShapePath, XYShapeFieldMapper.CONTENT_TYPE));

        final String shapeDocID = queryTestHelper.indexDocumentUsingWKT(indexedShapeIndex, indexedShapePath, "BBOX(-60, -50, -50, -60)");

        final SearchResponse searchResponse = queryTestHelper.searchUsingIndexedShapeIndex(
            indexName,
            indexedShapeIndex,
            indexedShapePath,
            shapeDocID,
            xyPointFieldName
        );
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 0);

        deleteIndex(indexName);
        deleteIndex(indexedShapeIndex);
    }

}
