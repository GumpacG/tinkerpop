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
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.StepTest;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.geo.Model;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * Tests the distance from the incoming geometry to a constant geometry or to the geometry a child
 * traversal supplies. The expected values come from simple geometry, never from
 * {@link org.apache.tinkerpop.gremlin.structure.geo.GeoOperations}: the 3-4-5 triangle for
 * {@link Model#CARTESIAN}, and one degree along the equator for {@link Model#GEOGRAPHIC}.
 */
public class DistanceStepTest extends StepTest {

    /**
     * The sphere the geographic expectations are taken on, the mean Earth radius of 6,371,008.8
     * meters that {@link org.apache.tinkerpop.gremlin.structure.geo.GeoOperations} measures
     * geographic distances on.
     */
    private static final double RADIUS_METERS = 6371008.8;

    @Override
    protected List<Traversal> getTraversals() {
        return Arrays.asList(
                __.distance(Geometry.fromWkt("POINT(3 4)")),
                __.distance(Geometry.fromWkt("POINT(0 0)")),
                __.distance(__.constant(Geometry.fromWkt("POINT(3 4)"))));
    }

    // ------------------------------------------------------------------------
    // constant geometry
    // ------------------------------------------------------------------------

    @Test
    public void shouldMeasurePlanarDistance() {
        assertEquals(5.0, __.__(Geometry.fromWkt("POINT(0 0)")).distance(Geometry.fromWkt("POINT(3 4)")).next(), 0.0);
    }

    @Test
    public void shouldMeasureGeographicDistanceInMeters() {
        // one degree of longitude on the equator is one degree of a great circle, held to a
        // relative tolerance because the radius above is the mean Earth radius rounded
        final double expected = RADIUS_METERS * Math.toRadians(1.0);
        assertEquals(expected,
                __.__(Geometry.fromWkt("POINT(0 0)", Model.GEOGRAPHIC))
                        .distance(Geometry.fromWkt("POINT(1 0)", Model.GEOGRAPHIC)).next(), expected * 1e-6);
    }

    @Test
    public void shouldPassNullThrough() {
        assertNull(__.__((Object) null).distance(Geometry.fromWkt("POINT(3 4)")).next());
    }

    @Test
    public void shouldThrowWhenIncomingValueIsNotGeometry() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__("POINT(0 0)").distance(Geometry.fromWkt("POINT(3 4)")).next());
        assertThat(e.getMessage(), containsString("distance() can only be applied to a Geometry, encountered "
                + String.class));
    }

    @Test
    public void shouldThrowWhenConstantGeometryIsNull() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.distance((Geometry) null));
        assertThat(e.getMessage(), containsString(
                "distance() needs a geometry to measure to, which cannot be null"));
    }

    // ------------------------------------------------------------------------
    // child traversal
    // ------------------------------------------------------------------------

    @Test
    public void shouldMeasureDistanceToTraversalGeometry() {
        assertEquals(5.0, __.__(Geometry.fromWkt("POINT(0 0)"))
                .distance(__.constant(Geometry.fromWkt("POINT(3 4)"))).next(), 0.0);
    }

    @Test
    public void shouldApplyTraversalToEachTraverser() {
        // the child yields the incoming geometry itself, so every distance is zero
        assertEquals(Arrays.asList(0.0, 0.0),
                __.__(Geometry.fromWkt("POINT(0 0)"), Geometry.fromWkt("POINT(3 4)"))
                        .distance(__.identity()).toList());
    }

    @Test
    public void shouldThrowWhenTraversalProducesNoResult() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(Geometry.fromWkt("POINT(0 0)")).distance(__.not(__.identity())).next());
        assertThat(e.getMessage(), containsString("distance() needs a geometry from"));
        assertThat(e.getMessage(), containsString("which produced no result"));
    }

    @Test
    public void shouldThrowWhenTraversalProducesNull() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(Geometry.fromWkt("POINT(0 0)")).distance(__.constant(null)).next());
        assertThat(e.getMessage(), containsString("distance() needs a Geometry from its traversal, encountered null"));
    }

    @Test
    public void shouldThrowWhenTraversalProducesNonGeometry() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__(Geometry.fromWkt("POINT(0 0)")).distance(__.constant("POINT(3 4)")).next());
        assertThat(e.getMessage(), containsString("distance() needs a Geometry from its traversal, encountered "
                + String.class));
    }

    @Test
    public void shouldThrowWhenTraversalIsNull() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.distance((Traversal<?, ?>) null));
        assertThat(e.getMessage(), containsString(
                "distance() needs a traversal to take the geometry from, which cannot be null"));
    }

    /**
     * The child traversal is cloned with the step, so that the clone does not share the child the
     * original walks.
     */
    @Test
    public void shouldCloneTheChildTraversal() {
        final DistanceStep<?> step = (DistanceStep<?>) __.__(Geometry.fromWkt("POINT(0 0)"))
                .distance(__.constant(Geometry.fromWkt("POINT(3 4)"))).asAdmin().getEndStep();
        final DistanceStep<?> clone = step.clone();

        assertNotSame(step.getGeometryTraversal(), clone.getGeometryTraversal());
    }

    @Test
    public void shouldTakeTheRequirementsOfTheChildTraversal() {
        final Traversal.Admin<?, Double> traversal = __.__(Geometry.fromWkt("POINT(0 0)"))
                .distance(__.path().constant(Geometry.fromWkt("POINT(3 4)"))).asAdmin();
        final Set<TraverserRequirement> requirements = traversal.getEndStep().getRequirements();
        assertThat(requirements, hasItem(TraverserRequirement.OBJECT));
        assertThat(requirements, hasItem(TraverserRequirement.PATH));
    }

    // ------------------------------------------------------------------------
    // extension
    // ------------------------------------------------------------------------

    /**
     * A provider substitutes its own computation by extending the step, reading the geometry
     * measured to from the step it extends.
     */
    @Test
    public void shouldAllowASubclassToSubstituteItsOwnComputation() {
        final Geometry other = Geometry.fromWkt("POINT(3 4)");
        final Traversal.Admin<Geometry, Geometry> start = __.__(Geometry.fromWkt("POINT(0 0)")).asAdmin();
        final FixedDistanceStep<Geometry> step = new FixedDistanceStep<>(start, other);
        final Traversal.Admin<Geometry, Double> traversal = start.addStep(step);

        assertEquals(other, step.getValue());
        assertEquals(42.0, traversal.next(), 0.0);
    }

    /**
     * A step that measures with something other than
     * {@link org.apache.tinkerpop.gremlin.structure.geo.GeoOperations}, as a provider would.
     */
    private static class FixedDistanceStep<S> extends DistanceStep<S> {

        private FixedDistanceStep(final Traversal.Admin traversal, final Geometry value) {
            super(traversal, value);
        }

        @Override
        protected Double map(final Traverser.Admin<S> traverser) {
            return 42.0;
        }
    }
}
