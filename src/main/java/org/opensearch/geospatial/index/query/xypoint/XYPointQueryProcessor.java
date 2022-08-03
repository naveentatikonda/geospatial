/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.query.xypoint;

import org.apache.lucene.search.Query;
import org.opensearch.common.geo.ShapeRelation;
import org.opensearch.geometry.Geometry;
import org.opensearch.geospatial.index.mapper.xypoint.XYPointFieldMapper;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.QueryShardException;

public class XYPointQueryProcessor {
    public Query shapeQuery(Geometry shape, String fieldName, ShapeRelation relation, QueryShardContext context) {
        validateIsXYPointFieldType(fieldName, context);
        // XYPoint only support "intersects"
        if (relation != ShapeRelation.INTERSECTS) {
            throw new QueryShardException(context, relation + " query relation not supported for Field [" + fieldName + "].");
        }

        return getVectorQueryFromShape(shape, fieldName, relation, context);
    }

    private void validateIsXYPointFieldType(String fieldName, QueryShardContext context) {
        MappedFieldType fieldType = context.fieldMapper(fieldName);
        if (fieldType instanceof XYPointFieldMapper.XYPointFieldType) {
            return;
        }
        throw new QueryShardException(
            context,
            "Expected " + XYPointFieldMapper.CONTENT_TYPE + " field type for Field [" + fieldName + "] but found " + fieldType.typeName()
        );
    }

    protected Query getVectorQueryFromShape(Geometry queryShape, String fieldName, ShapeRelation relation, QueryShardContext context) {
        XYPointQueryVisitor xyPointQueryVisitor = new XYPointQueryVisitor(context, context.fieldMapper(fieldName), fieldName, relation);
        return queryShape.visit(xyPointQueryVisitor);
    }
}
