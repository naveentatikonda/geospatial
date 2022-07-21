/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.mapper.xypoint;

import static org.opensearch.index.mapper.AbstractGeometryFieldMapper.Names.IGNORE_Z_VALUE;

import java.io.IOException;
import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.opensearch.OpenSearchParseException;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.geometry.Geometry;
import org.opensearch.geometry.Point;
import org.opensearch.geometry.ShapeType;
import org.opensearch.geometry.utils.GeographyValidator;
import org.opensearch.geometry.utils.WellKnownText;

/**
 * Represents a point in a 2-dimensional planar coordinate system with no range limitations
 */
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class XYPoint implements ToXContentFragment {

    private double x;
    private double y;

    public XYPoint(String value) {
        this.resetFromString(value);
    }

    /**
     * To set x and y values
     * @param x x coordinate value
     * @param y y coordinate value
     * @return initialized XYPoint
     */
    public XYPoint reset(double x, double y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public XYPoint resetX(double x) {
        this.x = x;
        return this;
    }

    public XYPoint resetY(double y) {
        this.y = y;
        return this;
    }

    public XYPoint resetFromString(String value) {
        return resetFromString(value, false);
    }

    public XYPoint resetFromString(String value, final boolean ignoreZValue) {
        if (value.toLowerCase(Locale.ROOT).contains("point")) {
            return resetFromWKT(value, ignoreZValue);
        } else {
            return resetFromCoordinates(value, ignoreZValue);
        }
    }

    public XYPoint resetFromCoordinates(String value, final boolean ignoreZValue) {
        String[] vals = value.split(",");
        if (vals.length > 3) {
            throw new OpenSearchParseException("failed to parse [{}], expected 2 or 3 coordinates " + "but found: [{}]", vals.length);
        }
        final double x;
        final double y;
        try {
            x = Double.parseDouble(vals[0].trim());
        } catch (NumberFormatException ex) {
            throw new OpenSearchParseException("x must be a number");
        }
        try {
            y = Double.parseDouble(vals[1].trim());
        } catch (NumberFormatException ex) {
            throw new OpenSearchParseException("y must be a number");
        }
        if (vals.length > 2) {
            XYPoint.assertZValue(ignoreZValue, Double.parseDouble(vals[2].trim()));
        }
        return reset(x, y);
    }

    private XYPoint resetFromWKT(String value, boolean ignoreZValue) {
        Geometry geometry;
        try {
            geometry = new WellKnownText(false, new GeographyValidator(ignoreZValue)).fromWKT(value);
        } catch (Exception e) {
            throw new OpenSearchParseException("Invalid WKT format", e);
        }
        if (geometry.type() != ShapeType.POINT) {
            throw new OpenSearchParseException("[geo_point] supports only POINT among WKT primitives, " + "but found " + geometry.type());
        }
        Point point = (Point) geometry;
        return reset(point.getY(), point.getX());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XYPoint)) return false;
        XYPoint point = (XYPoint) o;
        return point.x == x && point.y == y;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Point(");
        sb.append(x);
        sb.append(",");
        sb.append(y);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return null;
    }

    public static double assertZValue(final boolean ignoreZValue, double zValue) {
        if (ignoreZValue == false) {
            throw new OpenSearchParseException(
                "Exception parsing coordinates: found Z value [{}] but [{}] " + "parameter is [{}]",
                zValue,
                IGNORE_Z_VALUE,
                ignoreZValue
            );
        }
        return zValue;
    }
}
