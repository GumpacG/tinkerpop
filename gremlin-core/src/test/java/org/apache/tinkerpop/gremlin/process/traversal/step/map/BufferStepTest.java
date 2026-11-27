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

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.StepTest;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.geo.Polygon;
import org.junit.Test;
import org.locationtech.jts.io.WKTReader;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests the step that buffers the incoming geometry: the distance it is built with, the input it
 * refuses, and that it returns what
 * {@link org.apache.tinkerpop.gremlin.structure.geo.GeoOperations#buffer(Geometry, double)} defines.
 */
public class BufferStepTest extends StepTest {

    @Override
    protected List<Traversal> getTraversals() {
        return Arrays.asList(__.buffer(1.0), __.buffer(2.0));
    }

    @Test
    public void shouldPassNullThrough() {
        assertNull(__.__((Object) null).buffer(1.0).next());
    }

    @Test
    public void shouldBufferPlanarPointToAPolygon() {
        final Geometry buffered = __.__(Geometry.fromWkt("POINT(0 0)")).buffer(1.0).next();

        assertThat(buffered, instanceOf(Polygon.class));
        assertEquals(0, buffered.srid());
    }

    /**
     * The distance the step is built with is the one the measure is taken at: the buffer of a
     * planar point by 2 coordinate units holds a position 1.5 units away, and the buffer of the
     * same point by 1 unit does not.
     */
    @Test
    public void shouldBufferAtTheConfiguredDistance() {
        final Geometry origin = Geometry.fromWkt("POINT(0 0)");
        final double[] probe = {1.5, 0.0};

        final Geometry wide = __.__(origin).buffer(2.0).next();
        final Geometry narrow = __.__(origin).buffer(1.0).next();

        assertTrue("expected " + wide + " to hold POINT(1.5 0)", holds(wide, probe));
        assertFalse("expected " + narrow + " not to hold POINT(1.5 0)", holds(narrow, probe));
    }

    /**
     * Returns true if a geometry holds a position, read through JTS from the geometry's WKT so
     * that the answer owes nothing to the conversion the step uses.
     */
    private static boolean holds(final Geometry geometry, final double[] position) {
        final WKTReader reader = new WKTReader();
        try {
            return reader.read(geometry.toWKT())
                    .contains(reader.read("POINT(" + position[0] + " " + position[1] + ")"));
        } catch (Exception e) {
            throw new AssertionError("could not read " + geometry.toWKT() + " as JTS", e);
        }
    }

    @Test
    public void shouldThrowWhenIncomingValueIsNotGeometry() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__("POINT(0 0)").buffer(1.0).next());
        assertThat(e.getMessage(), containsString("buffer() can only be applied to a Geometry, encountered "
                + String.class));
    }

    @Test
    public void shouldThrowWhenDistanceIsNotFinite() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> __.buffer(Double.NaN)).getMessage(),
                containsString("must be a finite number"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.buffer(Double.POSITIVE_INFINITY)).getMessage(),
                containsString("must be a finite number"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> __.buffer(Double.NEGATIVE_INFINITY)).getMessage(),
                containsString("must be a finite number"));
    }

    /**
     * A distance of -0.0 buffers to the same region as 0.0, so the two steps hash alike.
     */
    @Test
    public void shouldTreatNegativeZeroDistanceAsZero() {
        final Step<?, ?> zero = __.buffer(0.0).asAdmin().getEndStep();
        final Step<?, ?> negativeZero = __.buffer(-0.0).asAdmin().getEndStep();

        assertEquals(zero.hashCode(), negativeZero.hashCode());
    }
}
