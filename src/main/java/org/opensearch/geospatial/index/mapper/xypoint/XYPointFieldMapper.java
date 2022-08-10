/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.mapper.xypoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.XYDocValuesField;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.opensearch.common.Explicit;
import org.opensearch.common.geo.ShapeRelation;
import org.opensearch.geometry.Geometry;
import org.opensearch.geospatial.index.mapper.xyshape.XYShapeQueryable;
import org.opensearch.geospatial.index.query.xypoint.XYPointQueryProcessor;
import org.opensearch.index.mapper.AbstractPointGeometryFieldMapper;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.mapper.ParseContext;
import org.opensearch.index.query.QueryShardContext;

/**
 *  FieldMapper for indexing {@link XYPoint} points
 *
 * @author Naveen Tatikonda
 */
public class XYPointFieldMapper extends AbstractPointGeometryFieldMapper<
    List<org.opensearch.geospatial.index.mapper.xypoint.XYPoint>,
    List<? extends XYPoint>> {
    public static final String CONTENT_TYPE = "xy_point";
    private static final FieldType FIELD_TYPE = new FieldType();

    static {
        FIELD_TYPE.setStored(false);
        FIELD_TYPE.setIndexOptions(IndexOptions.DOCS);
        FIELD_TYPE.freeze();
    }

    private XYPointFieldMapper(
        String simpleName,
        FieldType fieldType,
        MappedFieldType mappedFieldType,
        MultiFields multiFields,
        Explicit<Boolean> ignoreMalformed,
        Explicit<Boolean> ignoreZValue,
        ParsedPoint nullValue,
        CopyTo copyTo
    ) {
        super(simpleName, fieldType, mappedFieldType, multiFields, ignoreMalformed, ignoreZValue, nullValue, copyTo);
    }

    @Override
    protected void addStoredFields(ParseContext context, List<? extends XYPoint> points) {
        for (XYPoint point : points) {
            context.doc().add(new StoredField(fieldType().name(), point.toString()));
        }
    }

    @Override
    protected void addDocValuesFields(String name, List<? extends XYPoint> points, List<IndexableField> fields, ParseContext context) {
        for (XYPoint point : points) {
            context.doc().add(new XYDocValuesField(fieldType().name(), point.getX(), point.getY()));
        }
    }

    @Override
    protected void addMultiFields(ParseContext context, List<? extends XYPoint> points) {
        // Any other fields will not be added
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public XYPointFieldType fieldType() {
        return (XYPointFieldType) mappedFieldType;
    }

    /**
     * Builder class to create an instance of {@link XYPointFieldMapper}
     */
    public static class XYPointFieldMapperBuilder extends AbstractPointGeometryFieldMapper.Builder<
        XYPointFieldMapperBuilder,
        XYPointFieldType> {

        public XYPointFieldMapperBuilder(String fieldName) {
            super(fieldName, FIELD_TYPE);
            this.hasDocValues = true;
        }

        /**
         * Set the GeometryParser and GeometryIndexer for XYPointFieldType and create an instance of XYPointFieldMapper
         *
         * @param context  BuilderContext
         * @param simpleName  field name
         * @param fieldType  indicates the kind of data the field contains
         * @param multiFields  used to index same field in different ways for different purposes
         * @param ignoreMalformed  if true, malformed points are ignored else. If false(default) malformed points throw an exception
         * @param ignoreZValue  if true (default), third dimension is ignored. If false, points containing more than two dimension throw an exception
         * @param nullValue  used as a substitute for any explicit null values
         * @param copyTo  CopyTo instance
         * @return instance of XYPointFieldMapper
         */
        @Override
        public XYPointFieldMapper build(
            BuilderContext context,
            String simpleName,
            FieldType fieldType,
            MultiFields multiFields,
            Explicit<Boolean> ignoreMalformed,
            Explicit<Boolean> ignoreZValue,
            ParsedPoint nullValue,
            CopyTo copyTo
        ) {
            var processor = new XYPointQueryProcessor();
            XYPointFieldType xyPointFieldType = new XYPointFieldType(
                buildFullName(context),
                indexed,
                this.fieldType.stored(),
                hasDocValues,
                meta,
                processor
            );

            xyPointFieldType.setGeometryParser(
                new PointParser<>(name, org.opensearch.geospatial.index.mapper.xypoint.XYPoint::new, (parser, point) -> {
                    XYPointParser.parseXYPoint(parser, point, ignoreZValue().value());
                    return point;
                }, (org.opensearch.geospatial.index.mapper.xypoint.XYPoint) nullValue, ignoreZValue.value(), ignoreMalformed.value())
            );
            xyPointFieldType.setGeometryIndexer(new XYPointIndexer(xyPointFieldType.name()));
            return new XYPointFieldMapper(name, fieldType, xyPointFieldType, multiFields, ignoreMalformed, ignoreZValue, nullValue, copyTo);
        }
    }

    /**
     * Concrete field type for xy_point
     */
    public static class XYPointFieldType extends AbstractPointGeometryFieldType<
        List<org.opensearch.geospatial.index.mapper.xypoint.XYPoint>,
        List<? extends XYPoint>> implements XYShapeQueryable {
        private final XYPointQueryProcessor queryProcessor;

        public XYPointFieldType(
            String name,
            boolean indexed,
            boolean stored,
            boolean hasDocValues,
            Map<String, String> meta,
            XYPointQueryProcessor processor
        ) {
            super(name, indexed, stored, hasDocValues, meta);
            this.queryProcessor = Objects.requireNonNull(processor, "query processor cannot be null");
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        /**
         * Finds all previously indexed shapes that comply the given {@link ShapeRelation} with
         * the specified {@link Geometry}.
         *
         * @param geometry  query parameter to search indexed shapes
         * @param fieldName field name that contains indexed shapes
         * @param relation  relation between search shape and indexed shape
         * @param context   instance of {@link QueryShardContext}
         * @return Lucene {@link Query} to find indexed shapes based on given geometry
         */
        @Override
        public Query shapeQuery(Geometry geometry, String fieldName, ShapeRelation relation, QueryShardContext context) {
            return queryProcessor.shapeQuery(geometry, fieldName, relation, context);
            // GeometryVisitor<List<XYGeometry>, RuntimeException> visitor = new XYShapeQueryVisitor(fieldName, context);
            // return this.queryProcessor.shapeQuery(geometry, fieldName, relation, visitor, context);
        }
    }
}
