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
import org.apache.tinkerpop.gremlin.structure.geo.GeoOperations;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Reference implementation for the step that returns the region within a fixed distance of the
 * incoming {@link Geometry}. The region is the one
 * {@link GeoOperations#buffer(Geometry, double)} defines: coordinate units for
 * {@link org.apache.tinkerpop.gremlin.structure.geo.Model#CARTESIAN} and meters for
 * {@link org.apache.tinkerpop.gremlin.structure.geo.Model#GEOGRAPHIC}.
 *
 * @since 4.0.0
 */
public class BufferStep<S> extends ScalarMapStep<S, Geometry> {

    private final double distance;

    public BufferStep(final Traversal.Admin traversal, final double distance) {
        super(traversal);
        GeoOperations.validateBufferDistance(distance);
        this.distance = distance;
    }

    @Override
    protected Geometry map(final Traverser.Admin<S> traverser) {
        final Object object = traverser.get();
        if (object == null) {
            return null;
        }
        if (!(object instanceof Geometry)) {
            throw new IllegalArgumentException(String.format(
                    "buffer() can only be applied to a Geometry, encountered %s", object.getClass()));
        }
        return GeoOperations.buffer((Geometry) object, distance);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return Collections.singleton(TraverserRequirement.OBJECT);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        // -0.0 and 0.0 buffer to the same region, so they hash alike
        result = 31 * result + Double.hashCode(distance == 0.0 ? 0.0 : distance);
        return result;
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, distance);
    }

    public double getDistance() {
        return this.distance;
    }
}
