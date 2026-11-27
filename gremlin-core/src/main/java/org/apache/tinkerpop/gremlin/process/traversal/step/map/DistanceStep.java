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
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalProduct;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalUtil;
import org.apache.tinkerpop.gremlin.structure.geo.GeoOperations;
import org.apache.tinkerpop.gremlin.structure.geo.Geometry;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Reference implementation for the step that measures the distance from the incoming
 * {@link Geometry} to another geometry, which is either a constant or the result of a child
 * traversal taken for each traverser. In the traversal form the first result the child produces
 * for the traverser is the geometry measured to. The distance is the one
 * {@link GeoOperations#distance(Geometry, Geometry)} defines: coordinate units for
 * {@link org.apache.tinkerpop.gremlin.structure.geo.Model#CARTESIAN} and meters for
 * {@link org.apache.tinkerpop.gremlin.structure.geo.Model#GEOGRAPHIC}.
 *
 * @since 4.0.0
 */
public class DistanceStep<S> extends ScalarMapStep<S, Double> implements TraversalParent {

    private Geometry value;
    private Traversal.Admin<S, ?> geometryTraversal;

    public DistanceStep(final Traversal.Admin traversal, final Geometry value) {
        super(traversal);
        if (value == null) {
            throw new IllegalArgumentException("distance() needs a geometry to measure to, which cannot be null");
        }
        this.value = value;
    }

    public DistanceStep(final Traversal.Admin traversal, final Traversal<?, ?> geometryTraversal) {
        super(traversal);
        if (geometryTraversal == null) {
            throw new IllegalArgumentException("distance() needs a traversal to take the geometry from, which cannot be null");
        }
        this.geometryTraversal = this.integrateChild(geometryTraversal.asAdmin());
    }

    @Override
    protected Double map(final Traverser.Admin<S> traverser) {
        final Object object = traverser.get();
        if (object == null) {
            return null;
        }
        if (!(object instanceof Geometry)) {
            throw new IllegalArgumentException(String.format(
                    "distance() can only be applied to a Geometry, encountered %s", object.getClass()));
        }
        return GeoOperations.distance((Geometry) object, otherGeometry(traverser));
    }

    /**
     * Returns the geometry to measure to: the constant, or the first result of the child traversal
     * for this traverser.
     */
    private Geometry otherGeometry(final Traverser.Admin<S> traverser) {
        if (geometryTraversal == null) {
            return value;
        }
        final TraversalProduct product = TraversalUtil.produce(traverser, geometryTraversal);
        if (!product.isProductive()) {
            throw new IllegalArgumentException(String.format(
                    "distance() needs a geometry from %s, which produced no result", geometryTraversal));
        }
        final Object other = product.get();
        if (!(other instanceof Geometry)) {
            throw new IllegalArgumentException(String.format(
                    "distance() needs a Geometry from its traversal, encountered %s",
                    other == null ? null : other.getClass()));
        }
        return (Geometry) other;
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.getSelfAndChildRequirements(TraverserRequirement.OBJECT);
    }

    @Override
    public List<Traversal.Admin<?, ?>> getLocalChildren() {
        return geometryTraversal == null ? Collections.emptyList() : List.of(geometryTraversal);
    }

    @Override
    public void setTraversal(final Traversal.Admin<?, ?> parentTraversal) {
        super.setTraversal(parentTraversal);
        if (geometryTraversal != null) {
            integrateChild(geometryTraversal);
        }
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : geometryTraversal.hashCode());
        return result;
    }

    @Override
    public DistanceStep<S> clone() {
        final DistanceStep<S> clone = (DistanceStep<S>) super.clone();
        clone.geometryTraversal = null == this.geometryTraversal ? null : this.geometryTraversal.clone();
        return clone;
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, value == null ? geometryTraversal : value);
    }

    public Geometry getValue() {
        return this.value;
    }

    public Traversal.Admin<S, ?> getGeometryTraversal() {
        return this.geometryTraversal;
    }
}
