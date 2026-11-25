#region License

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

#endregion

using Gremlin.Net.Structure.Geo;

namespace Gremlin.Net.Process.Traversal
{
#pragma warning disable 1591

    /// <summary>
    ///     A <see cref="GeoP" /> filters geometry values by a spatial relationship.
    /// </summary>
    /// <remarks>
    ///     The value and the argument must share a coordinate model and SRID, or the predicate does not match.
    /// </remarks>
    public class GeoP : P
    {
        /// <summary>
        ///     Initializes a new instance of the <see cref="GeoP" /> class.
        /// </summary>
        /// <param name="operatorName">The name of the predicate.</param>
        /// <param name="value">The value of the predicate.</param>
        /// <param name="other">An optional other predicate that is used as an argument for this predicate.</param>
        public GeoP(string operatorName, object? value, P? other = null) : base(operatorName, value, other)
        {
        }

        /// <summary>
        ///     Determines if a geometry lies entirely inside the given geometry. A value that lies only on the
        ///     given geometry's boundary is not within it.
        /// </summary>
        public static GeoP GeoWithin(Geometry value)
        {
            return new GeoP("geoWithin", value);
        }

        /// <summary>
        ///     Determines if a geometry lies entirely inside the geometry a child traversal resolves for each
        ///     traverser.
        /// </summary>
        public static GeoP GeoWithin(ITraversal traversal)
        {
            return new GeoP("geoWithin", traversal);
        }

        /// <summary>
        ///     Determines if a geometry lies entirely inside the geometry a parameter holds.
        /// </summary>
        public static GeoP GeoWithin(IGValue value)
        {
            return new GeoP("geoWithin", value);
        }

        /// <summary>
        ///     Determines if a geometry entirely contains the given geometry. A geometry does not contain a value
        ///     that lies only on its boundary.
        /// </summary>
        public static GeoP GeoContains(Geometry value)
        {
            return new GeoP("geoContains", value);
        }

        /// <summary>
        ///     Determines if a geometry entirely contains the geometry a child traversal resolves for each
        ///     traverser.
        /// </summary>
        public static GeoP GeoContains(ITraversal traversal)
        {
            return new GeoP("geoContains", traversal);
        }

        /// <summary>
        ///     Determines if a geometry entirely contains the geometry a parameter holds.
        /// </summary>
        public static GeoP GeoContains(IGValue value)
        {
            return new GeoP("geoContains", value);
        }

        /// <summary>
        ///     Determines if a geometry shares at least one point with the given geometry, where a point of
        ///     either boundary counts. An empty geometry holds no point, so it intersects nothing.
        /// </summary>
        public static GeoP GeoIntersects(Geometry value)
        {
            return new GeoP("geoIntersects", value);
        }

        /// <summary>
        ///     Determines if a geometry shares at least one point with the geometry a child traversal resolves
        ///     for each traverser.
        /// </summary>
        public static GeoP GeoIntersects(ITraversal traversal)
        {
            return new GeoP("geoIntersects", traversal);
        }

        /// <summary>
        ///     Determines if a geometry shares at least one point with the geometry a parameter holds.
        /// </summary>
        public static GeoP GeoIntersects(IGValue value)
        {
            return new GeoP("geoIntersects", value);
        }

        /// <summary>
        ///     Determines if a geometry shares no point with the given geometry, which an empty geometry does
        ///     with every geometry.
        /// </summary>
        public static GeoP GeoDisjoint(Geometry value)
        {
            return new GeoP("geoDisjoint", value);
        }

        /// <summary>
        ///     Determines if a geometry shares no point with the geometry a child traversal resolves for each
        ///     traverser.
        /// </summary>
        public static GeoP GeoDisjoint(ITraversal traversal)
        {
            return new GeoP("geoDisjoint", traversal);
        }

        /// <summary>
        ///     Determines if a geometry shares no point with the geometry a parameter holds.
        /// </summary>
        public static GeoP GeoDisjoint(IGValue value)
        {
            return new GeoP("geoDisjoint", value);
        }

        /// <summary>
        ///     Determines if a geometry covers the same points as the given geometry, however each one is
        ///     written. Two empty geometries are equal whatever their kinds, and an empty geometry equals
        ///     nothing else.
        /// </summary>
        public static GeoP GeoEquals(Geometry value)
        {
            return new GeoP("geoEquals", value);
        }

        /// <summary>
        ///     Determines if a geometry covers the same points as the geometry a child traversal resolves for
        ///     each traverser.
        /// </summary>
        public static GeoP GeoEquals(ITraversal traversal)
        {
            return new GeoP("geoEquals", traversal);
        }

        /// <summary>
        ///     Determines if a geometry covers the same points as the geometry a parameter holds.
        /// </summary>
        public static GeoP GeoEquals(IGValue value)
        {
            return new GeoP("geoEquals", value);
        }
    }

#pragma warning restore 1591
}
