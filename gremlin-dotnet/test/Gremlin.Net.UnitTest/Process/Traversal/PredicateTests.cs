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

using Gremlin.Net.Process.Traversal;
using Gremlin.Net.Structure.Geo;
using Xunit;

namespace Gremlin.Net.UnitTest.Process.Traversal
{
    public class PredicateTests
    {
        [Fact]
        public void ShouldKeepOrderForNestedPredicate()
        {
            Assert.Equal("and(eq(a),lt(b))", P.Eq("a").And(P.Lt("b")).ToString());
        }

        [Fact]
        public void ShouldKeepOrderForDoubleNestedPredicate()
        {
            Assert.Equal("and(or(lt(b),gt(c)),neq(d))", P.Lt("b").Or(P.Gt("c")).And(P.Neq("d")).ToString());
        }

        [Fact]
        public void ShouldKeepOrderForTripleNestedPredicate()
        {
            Assert.Equal("and(or(lt(b),gt(c)),or(neq(d),gte(e)))",
                P.Lt("b").Or(P.Gt("c")).And(P.Neq("d").Or(P.Gte("e"))).ToString());
        }

        [Fact]
        public void ShouldHoldTheGeometryOfAGeoPredicate()
        {
            var point = new Point(1, 2);
            Assert.Equal(point, (Geometry) GeoP.GeoWithin(point).Value!);
            Assert.Equal("geoWithin(POINT(1 2))", GeoP.GeoWithin(point).ToString());
            Assert.Equal(point, (Geometry) GeoP.GeoContains(point).Value!);
            Assert.Equal("geoContains(POINT(1 2))", GeoP.GeoContains(point).ToString());
            Assert.Equal(point, (Geometry) GeoP.GeoIntersects(point).Value!);
            Assert.Equal("geoIntersects(POINT(1 2))", GeoP.GeoIntersects(point).ToString());
            Assert.Equal(point, (Geometry) GeoP.GeoDisjoint(point).Value!);
            Assert.Equal("geoDisjoint(POINT(1 2))", GeoP.GeoDisjoint(point).ToString());
            Assert.Equal(point, (Geometry) GeoP.GeoEquals(point).Value!);
            Assert.Equal("geoEquals(POINT(1 2))", GeoP.GeoEquals(point).ToString());
        }

        [Fact]
        public void ShouldHoldTheParameterOfAGeoPredicate()
        {
            var zone = new GValue<Geometry>("zone", new Point(1, 2));
            Assert.Equal(zone, (IGValue) GeoP.GeoWithin(zone).Value!);
            Assert.Equal(zone, (IGValue) GeoP.GeoContains(zone).Value!);
            Assert.Equal(zone, (IGValue) GeoP.GeoIntersects(zone).Value!);
            Assert.Equal(zone, (IGValue) GeoP.GeoDisjoint(zone).Value!);
            Assert.Equal(zone, (IGValue) GeoP.GeoEquals(zone).Value!);
        }

        [Fact]
        public void ShouldHoldTheTraversalOfAGeoPredicate()
        {
            var traversal = __.Select<object>("zone");
            Assert.Equal(traversal, (ITraversal) GeoP.GeoWithin(traversal).Value!);
            Assert.Equal(traversal, (ITraversal) GeoP.GeoContains(traversal).Value!);
            Assert.Equal(traversal, (ITraversal) GeoP.GeoIntersects(traversal).Value!);
            Assert.Equal(traversal, (ITraversal) GeoP.GeoDisjoint(traversal).Value!);
            Assert.Equal(traversal, (ITraversal) GeoP.GeoEquals(traversal).Value!);
        }

        [Fact]
        public void ShouldConnectGeoPredicates()
        {
            var and = GeoP.GeoWithin(new Point(1, 2)).And(GeoP.GeoContains(new Point(3, 4)));
            Assert.Equal("and(geoWithin(POINT(1 2)),geoContains(POINT(3 4)))", and.ToString());
            var or = GeoP.GeoWithin(new Point(1, 2)).Or(GeoP.GeoContains(new Point(3, 4)));
            Assert.Equal("or(geoWithin(POINT(1 2)),geoContains(POINT(3 4)))", or.ToString());
        }
    }
}