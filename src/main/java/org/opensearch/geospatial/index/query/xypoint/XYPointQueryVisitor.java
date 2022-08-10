/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.query.xypoint;

import static org.opensearch.geospatial.index.common.xyshape.XYShapeConverter.*;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

import lombok.AllArgsConstructor;

import org.apache.lucene.document.XYDocValuesField;
import org.apache.lucene.document.XYPointField;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.opensearch.common.geo.ShapeRelation;
import org.opensearch.geometry.Circle;
import org.opensearch.geometry.Geometry;
import org.opensearch.geometry.GeometryCollection;
import org.opensearch.geometry.GeometryVisitor;
import org.opensearch.geometry.Line;
import org.opensearch.geometry.LinearRing;
import org.opensearch.geometry.MultiLine;
import org.opensearch.geometry.MultiPoint;
import org.opensearch.geometry.MultiPolygon;
import org.opensearch.geometry.Point;
import org.opensearch.geometry.Polygon;
import org.opensearch.geometry.Rectangle;
import org.opensearch.geometry.ShapeType;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.QueryShardException;

@AllArgsConstructor
public class XYPointQueryVisitor implements GeometryVisitor<Query, RuntimeException> {
    QueryShardContext context;
    MappedFieldType fieldType;
    String fieldName;
    ShapeRelation relation;

    @Override
    public Query visit(Line line) {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.LINESTRING.name())
        );
    }

    @Override
    // don't think this is called directly
    public Query visit(LinearRing ring) {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.LINEARRING.name())
        );
    }

    @Override
    public Query visit(MultiLine multiLine) {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.MULTILINESTRING)
        );
    }

    @Override
    public Query visit(MultiPoint multiPoint) {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.MULTIPOINT)
        );
    }

    @Override
    public Query visit(Point point) {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.POINT)
        );
    }

    @Override
    public Query visit(Circle circle) throws RuntimeException {
        throw new QueryShardException(
            context,
            String.format(Locale.getDefault(), "Field [%s] found an unsupported shape [%s]", fieldName, ShapeType.CIRCLE)
        );
    }

    @Override
    public Query visit(Rectangle rectangle) {
        Objects.requireNonNull(rectangle, "Rectangle cannot be null");
        XYRectangle xyRectangle = toXYRectangle(rectangle);
        Query query = XYPointField.newBoxQuery(fieldName, xyRectangle.minX, xyRectangle.maxX, xyRectangle.minY, xyRectangle.maxY);
        if (fieldType.hasDocValues()) {
            Query dvQuery = XYDocValuesField.newSlowBoxQuery(
                fieldName,
                xyRectangle.minX,
                xyRectangle.maxX,
                xyRectangle.minY,
                xyRectangle.maxY
            );
            query = new IndexOrDocValuesQuery(query, dvQuery);
        }
        return query;
    }

    @Override
    public Query visit(MultiPolygon multiPolygon) {
        Objects.requireNonNull(multiPolygon, "Multi Polygon cannot be null");
        return visitCollection(multiPolygon);
    }

    @Override
    public Query visit(Polygon polygon) {
        Objects.requireNonNull(polygon, "Polygon cannot be null");
        return visitCollection(new GeometryCollection(Collections.singletonList(polygon)));
    }

    @Override
    public Query visit(GeometryCollection<?> collection) {
        if (collection.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        BooleanQuery.Builder bqb = new BooleanQuery.Builder();
        visit(bqb, collection);
        return bqb.build();
    }

    private void visit(BooleanQuery.Builder bqb, GeometryCollection<?> collection) {
        BooleanClause.Occur occur = BooleanClause.Occur.FILTER;
        for (Geometry shape : collection) {
            bqb.add(shape.visit(this), occur);
        }
    }

    private Query visitCollection(GeometryCollection<Polygon> collection) {
        if (collection.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        XYPolygon[] xyPolygons = new XYPolygon[collection.size()];
        for (int i = 0; i < collection.size(); i++) {
            xyPolygons[i] = toXYPolygon(collection.get(i));
        }
        Query query = XYPointField.newPolygonQuery(fieldName, xyPolygons);
        if (fieldType.hasDocValues()) {
            Query dvQuery = XYDocValuesField.newSlowPolygonQuery(fieldName, xyPolygons);
            query = new IndexOrDocValuesQuery(query, dvQuery);
        }
        return query;
    }

}
