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

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.StepTest;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.geo.Model;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * Tests the conversion of a value to a {@link Model#CARTESIAN} geometry: a geometry relabelled,
 * WKT or EWKT text parsed, a map of named coordinates read as a point, and the input the step
 * refuses.
 */
public class AsGeometryStepTest extends StepTest {

    @Override
    protected List<Traversal> getTraversals() {
        return Collections.singletonList(__.asGeometry());
    }

    private static Map<String, Object> map(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------------
    // null
    // ------------------------------------------------------------------------

    @Test
    public void shouldPassNullThrough() {
        assertNull(__.__((Object) null).asGeometry().next());
    }

    // ------------------------------------------------------------------------
    // geometry input
    // ------------------------------------------------------------------------

    @Test
    public void shouldReturnCartesianGeometryUnchanged() {
        final Geometry cartesian = Geometry.fromWkt("POINT(1 2)");
        assertEquals(cartesian, __.__(cartesian).asGeometry().next());
    }

    @Test
    public void shouldRelabelGeographicGeometryAsCartesian() {
        assertEquals(Geometry.fromWkt("POINT(1 2)"),
                __.__(Geometry.fromWkt("SRID=4326;POINT(1 2)")).asGeometry().next());
    }

    @Test
    public void shouldRelabelOtherGeometryAsCartesian() {
        assertEquals(Geometry.fromWkt("POINT(1 2)"),
                __.__(Geometry.fromWkt("POINT(1 2)", 32618)).asGeometry().next());
    }

    @Test
    public void shouldKeepZWhenRelabelling() {
        assertEquals(Geometry.fromWkt("POINT Z(1 2 3)"),
                __.__(Geometry.fromWkt("SRID=4326;POINT(1 2 3)")).asGeometry().next());
    }

    @Test
    public void shouldKeepStructureOfCollectionWhenRelabelling() {
        assertEquals(Geometry.fromWkt("GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0, 1 1))"),
                __.__(Geometry.fromWkt("SRID=4326;GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0, 1 1))"))
                        .asGeometry().next());
    }

    @Test
    public void shouldRelabelEmptyGeometry() {
        assertEquals(Geometry.fromWkt("POLYGON EMPTY"),
                __.__(Geometry.fromWkt("SRID=4326;POLYGON EMPTY")).asGeometry().next());
    }

    @Test
    public void shouldKeepZOfEmptyPointWhenRelabelling() {
        assertEquals(Geometry.fromWkt("POINT Z EMPTY"),
                __.__(Geometry.fromWkt("POINT Z EMPTY", Model.GEOGRAPHIC)).asGeometry().next());
    }

    @Test
    public void shouldKeepEmptyElementOfCollectionWhenRelabelling() {
        assertEquals(Geometry.fromWkt("MULTIPOINT(EMPTY)"),
                __.__(Geometry.fromWkt("MULTIPOINT(EMPTY)", Model.GEOGRAPHIC)).asGeometry().next());
    }

    /**
     * No step asks the conversion for a target model other than {@link Model#CARTESIAN} or
     * {@link Model#GEOGRAPHIC}, so this reaches an internal branch no traversal can reach and is
     * written against the package-private conversion rather than through a step.
     */
    @Test
    public void shouldThrowWhenTargetModelIsNeitherCartesianNorGeographic() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeometryConversions.convert(Geometry.fromWkt("POINT(1 2)"), Model.OTHER, "asGeometry"));
        assertThat(e.getMessage(), containsString("asGeometry() can only relabel a geometry as CARTESIAN or "
                + "GEOGRAPHIC, encountered the target model OTHER"));
    }

    // ------------------------------------------------------------------------
    // string input
    // ------------------------------------------------------------------------

    @Test
    public void shouldParseWktString() {
        assertEquals(Geometry.fromWkt("LINESTRING(0 0, 3 4)"),
                __.__("LINESTRING(0 0, 3 4)").asGeometry().next());
    }

    @Test
    public void shouldParseEwktStringAndRelabel() {
        assertEquals(Geometry.fromWkt("POINT(1 2)"), __.__("SRID=4326;POINT(1 2)").asGeometry().next());
    }

    @Test
    public void shouldParseEwktStringOfAnotherReferenceSystemAndRelabel() {
        assertEquals(Geometry.fromWkt("POINT(1 2)"), __.__("SRID=32618;POINT(1 2)").asGeometry().next());
    }

    @Test
    public void shouldParseEwktStringOfAnEmptyPointAndRelabel() {
        assertEquals(Geometry.fromWkt("POINT EMPTY"), __.__("SRID=4326;POINT EMPTY").asGeometry().next());
    }

    @Test
    public void shouldThrowWhenStringDoesNotParse() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__("not a geometry").asGeometry().next());
        assertThat(e.getMessage(), containsString("asGeometry() could not read the string as WKT or EWKT: "));
        // the message of the reader is carried in the text, with no cause behind it, so that a remote
        // caller reading the root cause of the exception sees the same text as an embedded caller
        assertNull(e.getCause());
    }

    // ------------------------------------------------------------------------
    // map input
    // ------------------------------------------------------------------------

    @Test
    public void shouldConvertMapOfLonAndLat() {
        assertEquals(Geometry.fromWkt("POINT(-97.7 30.2)"),
                __.__(map("lon", -97.7, "lat", 30.2)).asGeometry().next());
    }

    @Test
    public void shouldConvertMapOfXAndY() {
        assertEquals(Geometry.fromWkt("POINT(3 4)"), __.__(map("x", 3.0, "y", 4.0)).asGeometry().next());
    }

    @Test
    public void shouldConvertMapWithZ() {
        assertEquals(Geometry.fromWkt("POINT Z(1 2 3)"),
                __.__(map("x", 1.0, "y", 2.0, "z", 3.0)).asGeometry().next());
    }

    @Test
    public void shouldConvertMapWithZOfAnyNumberType() {
        assertEquals(Geometry.fromWkt("POINT Z(1 2 3)"),
                __.__(map("x", 1.0, "y", 2.0, "z", 3)).asGeometry().next());
    }

    @Test
    public void shouldConvertMapOfAnyNumberType() {
        assertEquals(Geometry.fromWkt("POINT(1 2)"), __.__(map("x", 1, "y", 2L)).asGeometry().next());
        assertEquals(Geometry.fromWkt("POINT(1 2)"), __.__(map("lon", (short) 1, "lat", 2.0f)).asGeometry().next());
    }

    @Test
    public void shouldConvertMapOfAnyMapType() {
        final Map<String, Object> hashMap = new HashMap<>();
        hashMap.put("lon", 1.0);
        hashMap.put("lat", 2.0);
        assertEquals(Geometry.fromWkt("POINT(1 2)"), __.__(hashMap).asGeometry().next());
    }

    @Test
    public void shouldThrowWhenMapKeyIsUnknown() {
        for (final String key : Arrays.asList("foo", "Lon", "longitude", "X")) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> __.__(map(key, 1.0, "lat", 2.0)).asGeometry().next());
            assertThat(e.getMessage(), containsString("does not know the map key \"" + key + "\""));
        }
    }

    @Test
    public void shouldThrowWhenMapKeyIsNotString() {
        final Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1, 1.0);
        map.put("lat", 2.0);
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(map).asGeometry().next());
        assertThat(e.getMessage(), containsString("needs string map keys"));
    }

    /**
     * A key and a number are the caller's to choose, so each is echoed back only as far as the
     * first 64 characters, with a marker for the rest, and the message stays as short as the
     * values it reports are long.
     */
    @Test
    public void shouldTruncateLongValuesItEchoes() {
        final String longKey = repeat('k', 10_000);
        final IllegalArgumentException key = assertThrows(IllegalArgumentException.class,
                () -> __.__(map(longKey, 1.0, "lat", 2.0)).asGeometry().next());
        assertThat(key.getMessage(), containsString("does not know the map key \"" + repeat('k', 64) + "...\""));
        assertThat(key.getMessage().length(), lessThan(250));

        final IllegalArgumentException value = assertThrows(IllegalArgumentException.class,
                () -> __.__(map("x", new LongNaN(), "y", 2.0)).asGeometry().next());
        assertThat(value.getMessage(), containsString("needs a finite number for the map key \"x\", encountered "
                + repeat('n', 64) + "..."));
        assertThat(value.getMessage().length(), lessThan(250));
    }

    private static String repeat(final char character, final int times) {
        final StringBuilder builder = new StringBuilder(times);
        for (int ix = 0; ix < times; ix++) {
            builder.append(character);
        }
        return builder.toString();
    }

    /**
     * A number whose own text is as long as its author cares to make it, which is what any
     * {@link Number} a caller hands in may be.
     */
    private static final class LongNaN extends Number {

        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0L;
        }

        @Override
        public float floatValue() {
            return Float.NaN;
        }

        @Override
        public double doubleValue() {
            return Double.NaN;
        }

        @Override
        public String toString() {
            return repeat('n', 10_000);
        }
    }

    @Test
    public void shouldThrowWhenMapMissesACoordinate() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 1.0)).asGeometry().next()).getMessage(),
                containsString("needs both \"lon\" and \"lat\""));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("y", 2.0)).asGeometry().next()).getMessage(),
                containsString("needs both \"x\" and \"y\""));
    }

    @Test
    public void shouldThrowWhenMapHasNoCoordinateKeys() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(Collections.emptyMap()).asGeometry().next()).getMessage(),
                containsString("needs a map with \"lon\" and \"lat\""));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("z", 3.0)).asGeometry().next()).getMessage(),
                containsString("needs a map with \"lon\" and \"lat\""));
    }

    @Test
    public void shouldThrowWhenMapMixesKeyPairs() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 1.0, "x", 2.0)).asGeometry().next()).getMessage(),
                containsString("cannot take the \"lon\"/\"lat\" and \"x\"/\"y\" key pairs in one map"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 1.0, "lat", 2.0, "x", 3.0)).asGeometry().next()).getMessage(),
                containsString("cannot take the \"lon\"/\"lat\" and \"x\"/\"y\" key pairs in one map"));
    }

    @Test
    public void shouldThrowWhenMapValueIsNotANumber() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", "1.0", "lat", 2.0)).asGeometry().next()).getMessage(),
                containsString("needs a number for the map key \"lon\""));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 1.0, "lat", null)).asGeometry().next()).getMessage(),
                containsString("needs a number for the map key \"lat\""));
    }

    @Test
    public void shouldThrowWhenMapValueIsAList() {
        // valueMap() hands a property over as a list, which is not a coordinate
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(map("lon", Collections.singletonList(1.0), "lat",
                        Collections.singletonList(2.0))).asGeometry().next());
        assertThat(e.getMessage(), containsString("needs a number for the map key \"lon\""));
    }

    @Test
    public void shouldThrowWhenMapValueIsNotFinite() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("x", Double.NaN, "y", 2.0)).asGeometry().next()).getMessage(),
                containsString("needs a finite number for the map key \"x\""));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("x", 1.0, "y", Double.POSITIVE_INFINITY)).asGeometry().next()).getMessage(),
                containsString("needs a finite number for the map key \"y\""));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("x", 1.0, "y", 2.0, "z", Double.NEGATIVE_INFINITY)).asGeometry().next()).getMessage(),
                containsString("needs a finite number for the map key \"z\""));
    }

    // ------------------------------------------------------------------------
    // other input
    // ------------------------------------------------------------------------

    @Test
    public void shouldThrowWhenInputIsAList() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(Arrays.asList(1.0, 2.0)).asGeometry().next());
        assertThat(e.getMessage(), containsString("asGeometry() can only take a Geometry, a WKT or EWKT string, "
                + "or a map of named coordinates as input"));
    }

    @Test
    public void shouldThrowWhenInputIsOfAnotherType() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(42).asGeometry().next()).getMessage(),
                containsString("can only take a Geometry, a WKT or EWKT string, or a map of named coordinates"));
    }
}
