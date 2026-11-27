/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.geo.Model;
import org.apache.tinkerpop.gremlin.structure.geo.Point;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Converts a value to a {@link Geometry} in a given {@link Model}, which is the whole of what
 * {@link AsGeometryStep} and {@link AsGeographyStep} do: the two differ only in the model they
 * convert to, and in what that model asks of the input. Every error names the step the conversion
 * was asked for, so that a caller reads which step refused the value.
 */
final class GeometryConversions {

    /**
     * The SRID of {@link Model#CARTESIAN}, as documented on that constant.
     */
    private static final int SRID_CARTESIAN = 0;

    /**
     * The SRID of {@link Model#GEOGRAPHIC}, as documented on that constant.
     */
    private static final int SRID_GEOGRAPHIC = 4326;

    private static final String LONGITUDE_KEY = "lon";
    private static final String LATITUDE_KEY = "lat";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";

    // a value echoed in a message is cut to 64 characters plus "..."
    private static final int MAX_ECHOED_LENGTH = 67;

    private GeometryConversions() {
        // static methods only, do not instantiate class
    }

    /**
     * Converts a value to a geometry in {@code model}, where null passes through as null.
     * <p>
     * A {@link Geometry} keeps its coordinates and its z and is relabelled with the model. A
     * {@link String} is parsed as WKT or EWKT and then relabelled. A {@link Map} becomes a
     * {@link Point} from the keys {@code lon} and {@code lat}, or {@code x} and {@code y}, with an
     * optional {@code z}.
     *
     * @throws IllegalArgumentException if the value is of any other type, if a string does not
     *         parse, if a map does not hold exactly one pair of coordinate keys with finite
     *         {@link Number} values under string keys, if {@code model} is
     *         {@link Model#GEOGRAPHIC} and the value is a geometry in a reference system other
     *         than {@link Model#CARTESIAN} and {@link Model#GEOGRAPHIC}, or if {@code model} is
     *         {@link Model#GEOGRAPHIC} and a coordinate is outside the longitude range -180 to 180
     *         or the latitude range -90 to 90
     */
    static Geometry convert(final Object object, final Model model, final String stepName) {
        if (object == null) {
            return null;
        }
        if (object instanceof Geometry) {
            return relabel((Geometry) object, model, stepName);
        }
        if (object instanceof String) {
            // the text is parsed first and relabelled after, so that an EWKT string in a reference
            // system of its own is read as written before the model of the step is applied to it
            return relabel(parse((String) object, stepName), model, stepName);
        }
        if (object instanceof Map) {
            return pointFromMap((Map<?, ?>) object, model, stepName);
        }
        throw new IllegalArgumentException(stepName + "() can only take a Geometry, a WKT or EWKT string, "
                + "or a map of named coordinates as input, encountered " + object.getClass());
    }

    /**
     * Returns the geometry a WKT or EWKT string describes, where text that does not parse is
     * reported under the step name, with the message of the reader carried in the text. The
     * reader's exception is not chained as the cause, since Gremlin Server reports only the root
     * cause of an exception and a remote caller would then read the reader's message alone,
     * without the step name.
     */
    private static Geometry parse(final String wktOrEwkt, final String stepName) {
        try {
            return Geometry.fromWkt(wktOrEwkt);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(stepName + "() could not read the string as WKT or EWKT: "
                    + e.getMessage());
        }
    }

    /**
     * Returns the geometry in {@code model}, with its coordinates and its z unchanged.
     * <p>
     * A geometry already in the model is returned as it is. Otherwise, it is rewritten through ISO
     * WKB, which carries every coordinate and the z flag, under the SRID of the model, so that the
     * reader applies the coordinate checks of the model to the result.
     */
    private static Geometry relabel(final Geometry geometry, final Model model, final String stepName) {
        if (model != Model.CARTESIAN && model != Model.GEOGRAPHIC) {
            throw new IllegalArgumentException(stepName + "() can only relabel a geometry as CARTESIAN or "
                    + "GEOGRAPHIC, encountered the target model " + model);
        }
        if (model == Model.GEOGRAPHIC && geometry.model() == Model.OTHER) {
            throw new IllegalArgumentException(stepName + "() cannot relabel a geometry with SRID "
                    + geometry.srid() + " as GEOGRAPHIC, since the coordinates of that reference system "
                    + "are not longitude and latitude");
        }
        final int srid = model == Model.GEOGRAPHIC ? SRID_GEOGRAPHIC : SRID_CARTESIAN;
        if (geometry.srid() == srid) {
            return geometry;
        }
        return underStepName(() -> Geometry.fromWkb(geometry.toWKB(), srid), stepName);
    }

    /**
     * Returns the geometry the supplier builds, where a coordinate the model refuses is reported
     * under the step name, with the message of the check carried in the text. That message names
     * the model and the range it allows, so the text says which coordinate was refused and why.
     * No cause is chained, for the reason {@link #parse(String, String)} gives.
     */
    private static <T extends Geometry> T underStepName(final Supplier<T> supplier, final String stepName) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(stepName + "() cannot read the coordinates: " + e.getMessage());
        }
    }

    /**
     * Returns the point a map of named coordinates describes. The keys are read exactly: no other
     * spelling, case or key is understood, and the two pairs cannot be mixed, so that a map which
     * does not say plainly which coordinates it holds is rejected rather than guessed at.
     */
    private static Point pointFromMap(final Map<?, ?> map, final Model model, final String stepName) {
        Number longitude = null;
        Number latitude = null;
        Number x = null;
        Number y = null;
        Number z = null;

        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new IllegalArgumentException(stepName + "() needs string map keys, encountered "
                        + (key == null ? null : key.getClass()));
            }
            switch ((String) key) {
                case LONGITUDE_KEY:
                    longitude = coordinate(entry.getValue(), LONGITUDE_KEY, stepName);
                    break;
                case LATITUDE_KEY:
                    latitude = coordinate(entry.getValue(), LATITUDE_KEY, stepName);
                    break;
                case X_KEY:
                    x = coordinate(entry.getValue(), X_KEY, stepName);
                    break;
                case Y_KEY:
                    y = coordinate(entry.getValue(), Y_KEY, stepName);
                    break;
                case Z_KEY:
                    z = coordinate(entry.getValue(), Z_KEY, stepName);
                    break;
                default:
                    throw new IllegalArgumentException(stepName + "() does not know the map key \""
                            + StringUtils.abbreviate((String) key, MAX_ECHOED_LENGTH)
                            + "\"; the keys are exactly \"lon\" and \"lat\", "
                            + "or \"x\" and \"y\", with an optional \"z\"");
            }
        }

        final boolean named = longitude != null || latitude != null;
        final boolean ordinate = x != null || y != null;
        if (named && ordinate) {
            throw new IllegalArgumentException(stepName + "() cannot take the \"lon\"/\"lat\" and "
                    + "\"x\"/\"y\" key pairs in one map");
        }
        if (named) {
            requireBoth(longitude, latitude, LONGITUDE_KEY, LATITUDE_KEY, stepName);
            return point(longitude, latitude, z, model, stepName);
        }
        if (ordinate) {
            requireBoth(x, y, X_KEY, Y_KEY, stepName);
            return point(x, y, z, model, stepName);
        }
        throw new IllegalArgumentException(stepName + "() needs a map with \"lon\" and \"lat\", "
                + "or with \"x\" and \"y\"");
    }

    /**
     * Returns the coordinate a map value holds, which is any finite {@link Number}. A list, which
     * is what {@code valueMap()} yields for a property, is not a number and is rejected.
     */
    private static Number coordinate(final Object value, final String key, final String stepName) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(stepName + "() needs a number for the map key \"" + key
                    + "\", encountered " + (value == null ? null : value.getClass()));
        }
        if (!Double.isFinite(((Number) value).doubleValue())) {
            throw new IllegalArgumentException(stepName + "() needs a finite number for the map key \"" + key
                    + "\", encountered " + StringUtils.abbreviate(String.valueOf(value), MAX_ECHOED_LENGTH));
        }
        return (Number) value;
    }

    private static void requireBoth(final Number first, final Number second, final String firstKey,
                                    final String secondKey, final String stepName) {
        if (first == null || second == null) {
            throw new IllegalArgumentException(stepName + "() needs both \"" + firstKey + "\" and \""
                    + secondKey + "\", the map holds only \"" + (first == null ? secondKey : firstKey) + "\"");
        }
    }

    private static Point point(final Number first, final Number second, final Number z, final Model model,
                               final String stepName) {
        return underStepName(() -> z == null
                ? Point.of(first.doubleValue(), second.doubleValue(), model)
                : Point.of(first.doubleValue(), second.doubleValue(), z.doubleValue(), model), stepName);
    }
}
