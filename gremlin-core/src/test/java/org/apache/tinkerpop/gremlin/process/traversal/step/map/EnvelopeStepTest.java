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
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * Tests the step that takes the bounding box of the incoming geometry: the input it refuses, and
 * that it returns what
 * {@link org.apache.tinkerpop.gremlin.structure.geo.GeoOperations#envelope(Geometry)} defines.
 */
public class EnvelopeStepTest extends StepTest {

    @Override
    protected List<Traversal> getTraversals() {
        return Collections.singletonList(__.envelope());
    }

    @Test
    public void shouldPassNullThrough() {
        assertNull(__.__((Object) null).envelope().next());
    }

    @Test
    public void shouldTakeTheEnvelopeOfALineString() {
        assertEquals(Geometry.fromWkt("POLYGON((0 0, 0 1, 2 1, 2 0, 0 0))"),
                __.__(Geometry.fromWkt("LINESTRING(0 0, 2 1)")).envelope().next());
    }

    @Test
    public void shouldThrowWhenIncomingValueIsNotGeometry() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> __.__("LINESTRING(0 0, 2 1)").envelope().next());
        assertThat(e.getMessage(), containsString("envelope() can only be applied to a Geometry, encountered "
                + String.class));
    }
}
