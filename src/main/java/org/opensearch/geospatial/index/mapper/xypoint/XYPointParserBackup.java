/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.index.mapper.xypoint;

import java.io.IOException;
import java.util.Collections;

import org.opensearch.OpenSearchParseException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentSubParser;
import org.opensearch.common.xcontent.support.MapXContentParser;

public class XYPointParserBackup {
    public static final String X = "x";
    public static final String Y = "y";

    public static XYPoint parseXYPoint(Object value, final boolean ignoreZValue, XYPoint point) throws OpenSearchParseException {
        try (
            XContentParser parser = new MapXContentParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                Collections.singletonMap("null_value", value),
                null
            )
        ) {
            parser.nextToken(); // start object
            parser.nextToken(); // field name
            parser.nextToken(); // field value
            return parseXYPoint(parser, point, ignoreZValue);
        } catch (IOException ex) {
            throw new OpenSearchParseException("error parsing xy_point", ex);
        }
    }

    public static XYPoint parseXYPoint(XContentParser parser, XYPoint point, final boolean ignoreZValue) throws IOException,
        OpenSearchParseException {
        double x = Double.NaN;
        double y = Double.NaN;
        NumberFormatException numberFormatException = null;

        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
            try (XContentSubParser subParser = new XContentSubParser(parser)) {
                while (subParser.nextToken() != XContentParser.Token.END_OBJECT) {
                    if (subParser.currentToken() == XContentParser.Token.FIELD_NAME) {
                        String field = subParser.currentName();
                        if (X.equals(field)) {
                            subParser.nextToken();
                            switch (subParser.currentToken()) {
                                case VALUE_NUMBER:
                                case VALUE_STRING:
                                    try {
                                        x = subParser.doubleValue(true);
                                    } catch (NumberFormatException e) {
                                        numberFormatException = e;
                                    }
                                    break;
                                default:
                                    throw new OpenSearchParseException("x must be a number");
                            }
                        } else if (Y.equals(field)) {
                            subParser.nextToken();
                            switch (subParser.currentToken()) {
                                case VALUE_NUMBER:
                                case VALUE_STRING:
                                    try {
                                        y = subParser.doubleValue(true);
                                    } catch (NumberFormatException e) {
                                        numberFormatException = e;
                                    }
                                    break;
                                default:
                                    throw new OpenSearchParseException("y must be a number");
                            }
                        } else {
                            throw new OpenSearchParseException("field must be either [{}] or [{}]", X, Y);
                        }
                    } else {
                        throw new OpenSearchParseException("token [{}] not allowed", subParser.currentToken());
                    }
                }
            }
            if (numberFormatException != null) {
                throw new OpenSearchParseException("[{}] and [{}] must be valid double values", numberFormatException, X, Y);
            } else if (Double.isNaN(x)) {
                throw new OpenSearchParseException("field [{}] missing", X);
            } else if (Double.isNaN(y)) {
                throw new OpenSearchParseException("field [{}] missing", Y);
            } else {
                return point.reset(x, y);
            }

        } else if (parser.currentToken() == XContentParser.Token.START_ARRAY) {
            try (XContentSubParser subParser = new XContentSubParser(parser)) {
                int element = 0;
                while (subParser.nextToken() != XContentParser.Token.END_ARRAY) {
                    if (subParser.currentToken() == XContentParser.Token.VALUE_NUMBER) {
                        element++;
                        if (element == 1) {
                            y = subParser.doubleValue();
                        } else if (element == 2) {
                            x = subParser.doubleValue();
                        } else if (element == 3) {
                            XYPoint.assertZValue(ignoreZValue, subParser.doubleValue());
                        } else {
                            throw new OpenSearchParseException("[xy_point] field type does not accept > 3 dimensions");
                        }
                    } else {
                        throw new OpenSearchParseException("numeric value expected");
                    }
                }
            }
            return point.reset(x, y);
        } else if (parser.currentToken() == XContentParser.Token.VALUE_STRING) {
            String val = parser.text();
            return point.resetFromString(val, ignoreZValue);
        } else {
            throw new OpenSearchParseException("xy_point expected");
        }
    }
}
