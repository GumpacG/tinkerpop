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
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.geo.Model;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Reference implementation for the step that converts the incoming value to a
 * {@link Model#GEOGRAPHIC} {@link Geometry}. The conversion is the one
 * {@link GeometryConversions} holds, taken to the geographic model, so the coordinates must be
 * longitude and latitude: a geometry in a reference system that is neither {@link Model#CARTESIAN}
 * nor {@link Model#GEOGRAPHIC} is rejected, as is a coordinate outside the longitude range -180 to
 * 180 or the latitude range -90 to 90.
 *
 * @since 4.0.0
 */
public final class AsGeographyStep<S> extends ScalarMapStep<S, Geometry> {

    public AsGeographyStep(final Traversal.Admin traversal) {
        super(traversal);
    }

    @Override
    protected Geometry map(final Traverser.Admin<S> traverser) {
        return GeometryConversions.convert(traverser.get(), Model.GEOGRAPHIC, "asGeography");
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return Collections.singleton(TraverserRequirement.OBJECT);
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this);
    }
}
