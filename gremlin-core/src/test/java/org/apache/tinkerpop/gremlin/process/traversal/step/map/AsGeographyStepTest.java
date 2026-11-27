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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests the conversion of a value to a {@link Model#GEOGRAPHIC} geometry, which takes the same
 * input as {@code asGeometry()} and reads the coordinates as longitude and latitude, so a geometry
 * in another reference system and a coordinate out of range are refused.
 */
public class AsGeographyStepTest extends StepTest {

    @Override
    protected List<Traversal> getTraversals() {
        return Collections.singletonList(__.asGeography());
    }

    private static Map<String, Object> map(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    public void shouldPassNullThrough() {
        assertNull(__.__((Object) null).asGeography().next());
    }

    @Test
    public void shouldRelabelCartesianGeometryAsGeographic() {
        final Geometry converted = __.__(Geometry.fromWkt("POINT(1 2)")).asGeography().next();
        assertEquals(Geometry.fromWkt("SRID=4326;POINT(1 2)"), converted);
        assertEquals(Model.GEOGRAPHIC, converted.model());
        assertEquals(4326, converted.srid());
    }

    @Test
    public void shouldReturnGeographicGeometryUnchanged() {
        final Geometry geographic = Geometry.fromWkt("SRID=4326;LINESTRING(0 0, 3 4)");
        assertEquals(geographic, __.__(geographic).asGeography().next());
    }

    @Test
    public void shouldKeepZWhenRelabelling() {
        final Geometry converted = __.__(Geometry.fromWkt("POINT Z(1 2 3)")).asGeography().next();
        assertEquals(Geometry.fromWkt("SRID=4326;POINT(1 2 3)"), converted);
        assertTrue(converted.hasZ());
    }

    @Test
    public void shouldKeepZOfCollectionWhenRelabelling() {
        final String wkt = "GEOMETRYCOLLECTION Z (POINT Z (1 2 3), LINESTRING Z (0 0 0, 1 1 1))";
        final Geometry converted = __.__(Geometry.fromWkt(wkt)).asGeography().next();
        assertEquals(Geometry.fromWkt(wkt, Model.GEOGRAPHIC), converted);
        assertEquals(Model.GEOGRAPHIC, converted.model());
        assertTrue(converted.hasZ());
    }

    @Test
    public void shouldParseWktStringAsGeographic() {
        final Geometry converted = __.__("POINT(-97.7 30.2)").asGeography().next();
        assertEquals(Geometry.fromWkt("POINT(-97.7 30.2)", Model.GEOGRAPHIC), converted);
        assertEquals(Model.GEOGRAPHIC, converted.model());
    }

    @Test
    public void shouldConvertMapOfLonAndLat() {
        final Geometry converted = __.__(map("lon", -97.7, "lat", 30.2)).asGeography().next();
        assertEquals(Geometry.fromWkt("POINT(-97.7 30.2)", Model.GEOGRAPHIC), converted);
        assertEquals(Model.GEOGRAPHIC, converted.model());
    }

    @Test
    public void shouldThrowWhenGeometryIsInAnotherReferenceSystem() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(Geometry.fromWkt("POINT(1 2)", 32618)).asGeography().next());
        assertThat(e.getMessage(), containsString("asGeography() cannot relabel a geometry with SRID 32618"));
    }

    @Test
    public void shouldThrowWhenEwktStringIsInAnotherReferenceSystem() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__("SRID=32618;POINT(1 2)").asGeography().next());
        assertThat(e.getMessage(), containsString("asGeography() cannot relabel a geometry with SRID 32618"));
    }

    @Test
    public void shouldThrowWhenGeometryCoordinateIsOutOfRange() {
        final IllegalArgumentException longitude = assertThrows(IllegalArgumentException.class,
                () -> __.__(Geometry.fromWkt("POINT(200 0)")).asGeography().next());
        assertThat(longitude.getMessage(), containsString("asGeography() cannot read the coordinates: "
                + "GEOGRAPHIC longitude must be in [-180, 180]"));
        // the message of the check is carried in the text, with no cause behind it, so that a remote
        // caller reading the root cause of the exception sees the same text as an embedded caller
        assertNull(longitude.getCause());
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(Geometry.fromWkt("LINESTRING(0 0, 0 91)")).asGeography().next()).getMessage(),
                containsString("asGeography() cannot read the coordinates: "
                        + "GEOGRAPHIC latitude must be in [-90, 90]"));
    }

    @Test
    public void shouldThrowWhenStringCoordinateIsOutOfRange() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__("POINT(-180.5 0)").asGeography().next()).getMessage(),
                containsString("asGeography() cannot read the coordinates: "
                        + "GEOGRAPHIC longitude must be in [-180, 180]"));
    }

    @Test
    public void shouldThrowWhenMapCoordinateIsOutOfRange() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 181.0, "lat", 0.0)).asGeography().next()).getMessage(),
                containsString("asGeography() cannot read the coordinates: "
                        + "GEOGRAPHIC longitude must be in [-180, 180]"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 0.0, "lat", -90.1)).asGeography().next()).getMessage(),
                containsString("asGeography() cannot read the coordinates: "
                        + "GEOGRAPHIC latitude must be in [-90, 90]"));
    }

    @Test
    public void shouldReportTheStepNameOnSharedInputErrors() {
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(Arrays.asList(1.0, 2.0)).asGeography().next()).getMessage(),
                containsString("asGeography() can only take a Geometry, a WKT or EWKT string, "
                        + "or a map of named coordinates as input"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__(map("lon", 1.0, "x", 2.0)).asGeography().next()).getMessage(),
                containsString("asGeography() cannot take the \"lon\"/\"lat\" and \"x\"/\"y\" key pairs"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.__("not a geometry").asGeography().next()).getMessage(),
                containsString("asGeography() could not read the string as WKT or EWKT: "));
    }
}
