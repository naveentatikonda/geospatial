/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.mapper.xypoint;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.XYPointField;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.opensearch.common.Explicit;
import org.opensearch.common.geo.ShapeRelation;
import org.opensearch.geometry.Geometry;
import org.opensearch.geometry.GeometryVisitor;
import org.opensearch.geospatial.index.mapper.xyshape.XYShapeQueryable;
import org.opensearch.geospatial.index.query.xyshape.XYShapeQueryProcessor;
import org.opensearch.geospatial.index.query.xyshape.XYShapeQueryVisitor;
import org.opensearch.index.mapper.*;
import org.opensearch.index.query.QueryShardContext;

/**
 *  FieldMapper for indexing {@link org.apache.lucene.document.XYShape}s.
 */
public class XYPointFieldMapper extends AbstractPointGeometryFieldMapper<List<ParsedXYPoint>, List<? extends XYPoint>> {
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
    protected void addStoredFields(ParseContext context, List<? extends XYPoint> geometry) {
        for (XYPoint point : geometry) {
            context.doc().add(new StoredField(fieldType().name(), point.toString()));
        }

    }

    @Override
    protected void addDocValuesFields(String name, List<? extends XYPoint> geometry, List<IndexableField> fields, ParseContext context) {
        for (XYPoint point : geometry) {
            context.doc().add(new XYPointField(fieldType().name(), point.getX(), point.getY()));
        }

    }

    @Override
    protected void addMultiFields(ParseContext context, List<? extends XYPoint> geometry) throws IOException {

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
            var processor = new XYShapeQueryProcessor();
            XYPointFieldType xyPointFieldType = new XYPointFieldType(
                buildFullName(context),
                indexed,
                this.fieldType.stored(),
                hasDocValues,
                meta,
                processor
            );

            xyPointFieldType.setGeometryParser(new PointParser<>(name, ParsedXYPoint::new, (parser, point) -> {
                XYPointParser.parseXYPoint(parser, point, ignoreZValue().value());
                return point;
            }, (ParsedXYPoint) nullValue, ignoreZValue.value(), ignoreMalformed.value()));
            xyPointFieldType.setGeometryIndexer(new XYPointIndexer(xyPointFieldType.name()));
            return new XYPointFieldMapper(name, fieldType, xyPointFieldType, multiFields, ignoreMalformed, ignoreZValue, nullValue, copyTo);
        }

        // @Override
        // public XYPointFieldMapper build(BuilderContext context) {
        // return new XYPointFieldMapper(
        // name,
        // fieldType,
        // buildPointFieldType(context),
        // multiFieldsBuilder.build(this, context),
        // ignoreMalformed(context),
        // ignoreZValue(),
        // nullValue,
        // copyTo
        // );
        // }
        //
        // private XYPointFieldType buildPointFieldType(BuilderContext context) {
        // var processor = new XYShapeQueryProcessor();
        // var fieldType = new XYPointFieldType(buildFullName(context), indexed, this.fieldType.stored(), hasDocValues, meta, processor);
        //
        //// fieldType.setGeometryParser(new PointParser<>(name, Point::new, (parser, point) -> {
        //// GeoUtils.parseGeoPoint(parser, point, ignoreZValue().value());
        //// return point;
        //// }, (Point) nullValue, ignoreZValue().value(), ignoreMalformed().value()));
        //
        //// var geometryParser = new GeometryParser(true, true, ignoreZValue().value());
        //// fieldType.setGeometryParser(new GeoShapeParser(geometryParser));
        //// GeometryVisitor<IndexableField[], RuntimeException> xyShapeIndexableVisitor = new XYShapeIndexableFieldsVisitor(
        //// fieldType.name()
        //// );
        ////
        //// fieldType.setGeometryIndexer(new XYPointIndexer(fieldType));
        // return fieldType;
        // }
        // }
    }

    public static class XYPointFieldType extends AbstractPointGeometryFieldType<List<ParsedXYPoint>, List<? extends XYPoint>>
        implements
            XYShapeQueryable {

        private final XYShapeQueryProcessor queryProcessor;

        public XYPointFieldType(
            String name,
            boolean indexed,
            boolean stored,
            boolean hasDocValues,
            Map<String, String> meta,
            XYShapeQueryProcessor processor
        ) {
            super(name, indexed, stored, hasDocValues, meta);
            this.queryProcessor = Objects.requireNonNull(processor, "query processor cannot be null");
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public Query shapeQuery(Geometry geometry, String fieldName, ShapeRelation relation, QueryShardContext context) {
            GeometryVisitor<List<XYGeometry>, RuntimeException> visitor = new XYShapeQueryVisitor(fieldName, context);
            return this.queryProcessor.shapeQuery(geometry, fieldName, relation, visitor, context);
        }

    }
}
