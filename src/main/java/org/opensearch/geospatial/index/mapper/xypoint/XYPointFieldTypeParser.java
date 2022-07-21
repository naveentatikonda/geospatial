/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.mapper.xypoint;

import java.util.Map;

import org.opensearch.index.mapper.AbstractPointGeometryFieldMapper;

public class XYPointFieldTypeParser extends AbstractPointGeometryFieldMapper.TypeParser {
    @Override
    protected AbstractPointGeometryFieldMapper.Builder newBuilder(String name, Map params) {
        return new XYPointFieldMapper.XYPointFieldMapperBuilder(name);
    }

    protected ParsedXYPoint parseNullValue(Object nullValue, boolean ignoreZValue, boolean ignoreMalformed) {
        ParsedXYPoint point = new ParsedXYPoint();
        XYPointParser.parseXYPoint(nullValue, ignoreZValue, point);
        // if (ignoreMalformed == false) {
        // if (point.lat() > 90.0 || point.lat() < -90.0) {
        // throw new IllegalArgumentException("illegal latitude value [" + point.lat() + "]");
        // }
        // if (point.lon() > 180.0 || point.lon() < -180) {
        // throw new IllegalArgumentException("illegal longitude value [" + point.lon() + "]");
        // }
        // } else {
        // GeoUtils.normalizePoint(point);
        // }
        return point;
    }

}
