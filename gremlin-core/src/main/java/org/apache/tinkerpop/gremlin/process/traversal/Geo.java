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
package org.apache.tinkerpop.gremlin.process.traversal;

import org.apache.tinkerpop.gremlin.structure.geo.GeoPredicates;

/**
 * {@link Geo} is a {@code BiPredicate} that evaluates a spatial relationship between a geometry
 * value and the argument of a {@link GeoP}. A provider strategy that recognizes one of these
 * values on a {@code HasContainer} can answer the filter from a spatial index instead.
 * <p>
 * A value that cannot be compared is {@code false} rather than an error: a value that is not a
 * geometry, a null, a value whose SRID differs from the argument's, and a value in a coordinate
 * system other than {@code CARTESIAN} or {@code GEOGRAPHIC}. An empty geometry can be compared, and
 * each predicate documents how it answers one. All math is 2D, so z is ignored.
 * <p>
 * Accuracy in the geographic model is described on {@link GeoP}.
 *
 * @since 4.0.0
 */
public enum Geo implements PBiPredicate<Object, Object> {

    /**
     * Evaluates if the value lies entirely inside the argument.
     *
     * @since 4.0.0
     */
    geoWithin {
        @Override
        public boolean test(final Object value, final Object argument) {
            return GeoPredicates.within(value, argument);
        }
    },

    /**
     * Evaluates if the value entirely contains the argument.
     *
     * @since 4.0.0
     */
    geoContains {
        @Override
        public boolean test(final Object value, final Object argument) {
            return GeoPredicates.contains(value, argument);
        }
    },

    /**
     * Evaluates if the value shares at least one point with the argument, where a point of either
     * boundary counts. An empty geometry holds no point, so it intersects nothing.
     *
     * @since 4.0.0
     */
    geoIntersects {
        @Override
        public boolean test(final Object value, final Object argument) {
            return GeoPredicates.intersects(value, argument);
        }
    },

    /**
     * Evaluates if the value shares no point with the argument, which an empty geometry does with
     * every geometry. It is the opposite of {@link #geoIntersects} for a value that can be
     * compared and, like it, {@code false} for one that cannot.
     *
     * @since 4.0.0
     */
    geoDisjoint {
        @Override
        public boolean test(final Object value, final Object argument) {
            return GeoPredicates.disjoint(value, argument);
        }
    },

    /**
     * Evaluates if the value covers the same points as the argument, however each one is written,
     * so a line with a redundant vertex equals the line without it. Two empty geometries are equal
     * whatever their kinds, and an empty geometry equals nothing else.
     *
     * @since 4.0.0
     */
    geoEquals {
        @Override
        public boolean test(final Object value, final Object argument) {
            return GeoPredicates.equalTo(value, argument);
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean test(final Object value, final Object argument);
}
