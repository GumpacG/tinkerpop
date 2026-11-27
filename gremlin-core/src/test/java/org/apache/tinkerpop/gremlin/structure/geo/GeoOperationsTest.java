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
package org.apache.tinkerpop.gremlin.structure.geo;

import org.junit.Test;
import org.locationtech.jts.io.WKTReader;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests the geometry operations: planar distance, geographic distance between points and between
 * other shapes, buffer, envelope, and the argument checks.
 * <p>
 * Every expected value is computed by the test itself, never by {@link GeoOperations}: a haversine
 * and a destination formula written here in plain {@link Math}, a shoelace area for a planar
 * ring, and for a shape the minimum of that haversine over positions sampled densely along its
 * edges, which are straight in longitude and latitude.
 */
public class GeoOperationsTest {

    /**
     * The sphere the test's own haversine is taken on, the mean Earth radius of 6,371,008.8
     * meters that {@link GeoOperations} measures geographic distances on.
     */
    private static final double RADIUS_METERS = 6371008.8;

    /**
     * The bar the operations hold to for shapes, 1%, from the accuracy promise of
     * {@link GeoOperations#distance(Geometry, Geometry)} for edges under about 100 km.
     */
    private static final double SHAPE_TOLERANCE = 0.01;

    /**
     * The box the random cases stay inside, far from the antimeridian and from both poles.
     */
    private static final double MAX_RANDOM_LONGITUDE = 10.0;
    private static final double MAX_RANDOM_LATITUDE = 75.0;

    // ------------------------------------------------------------------------
    // CARTESIAN
    // ------------------------------------------------------------------------

    @Test
    public void shouldMeasurePlanarPointToPoint() {
        assertEquals(5.0, GeoOperations.distance(Geometry.fromWkt("POINT(0 0)"), Geometry.fromWkt("POINT(3 4)")), 0.0);
    }

    @Test
    public void shouldMeasurePlanarPointInPolygonAsZero() {
        assertEquals(0.0, GeoOperations.distance(Geometry.fromWkt("POINT(2 2)"),
                Geometry.fromWkt("POLYGON((0 0, 4 0, 4 4, 0 4, 0 0))")), 0.0);
    }

    /**
     * A shape that holds another is at distance 0 from it, in either order, and so is a shape that
     * crosses one. The distance is taken between the nearest edges and vertices of the two, which
     * for a shape inside another are the edges of the ring around it, so the answer rests on the
     * geometries being read as intersecting rather than on those edges.
     */
    @Test
    public void shouldMeasurePlanarContainedShapesAsZero() {
        final Geometry square = Geometry.fromWkt("POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        final Geometry inside = Geometry.fromWkt("POLYGON((2 2, 3 2, 3 3, 2 3, 2 2))");
        final Geometry across = Geometry.fromWkt("LINESTRING(5 -5, 5 15)");

        assertEquals(0.0, GeoOperations.distance(square, inside), 0.0);
        assertEquals(0.0, GeoOperations.distance(inside, square), 0.0);
        assertEquals(0.0, GeoOperations.distance(square, across), 0.0);
        assertEquals(0.0, GeoOperations.distance(Geometry.fromWkt("LINESTRING(0 0, 4 4)"),
                Geometry.fromWkt("LINESTRING(0 4, 4 0)")), 0.0);
    }

    /**
     * A multi kind and a collection are measured as the nearest of their parts, and an empty part
     * holds no position and so contributes nothing.
     */
    @Test
    public void shouldMeasurePlanarCollectionsAsTheNearestOfTheirParts() {
        assertEquals(1.0, GeoOperations.distance(Geometry.fromWkt("MULTIPOINT((0 0), (5 0))"),
                Geometry.fromWkt("LINESTRING(6 -1, 6 1)")), 0.0);
        assertEquals(2.0, GeoOperations.distance(
                Geometry.fromWkt("GEOMETRYCOLLECTION(POINT EMPTY, POINT(1 1))"),
                Geometry.fromWkt("POINT(1 3)")), 0.0);
    }

    @Test
    public void shouldMeasurePlanarPointToLineString() {
        assertEquals(3.0, GeoOperations.distance(Geometry.fromWkt("POINT(2 3)"),
                Geometry.fromWkt("LINESTRING(0 0, 4 0)")), 0.0);
    }

    // ------------------------------------------------------------------------
    // GEOGRAPHIC
    // ------------------------------------------------------------------------

    /**
     * Austin to Dallas/Fort Worth, about 306 km, against the test's own haversine and against a
     * flat-earth estimate of the same pair that anchors the scale of the answer.
     */
    @Test
    public void shouldMeasureGeographicPointToPoint() {
        final Geometry austin = Geometry.fromWkt("POINT(-97.6698989868164 30.1944999694824)", Model.GEOGRAPHIC);
        final Geometry dfw = Geometry.fromWkt("POINT(-97.0380 32.8968)", Model.GEOGRAPHIC);

        final double expected = haversineMeters(30.1944999694824, -97.6698989868164, 32.8968, -97.0380);
        final double measured = GeoOperations.distance(austin, dfw);

        assertEquals(expected, measured, expected * 1e-6);
        assertEquals(306_400.0, measured, 3_064.0);
    }

    @Test
    public void shouldMeasureIntersectingGeographicPointAndPolygonAsZero() {
        final Geometry point = Geometry.fromWkt("POINT(0.5 0.5)", Model.GEOGRAPHIC);
        final Geometry polygon = Geometry.fromWkt("POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))", Model.GEOGRAPHIC);

        assertEquals(0.0, GeoOperations.distance(point, polygon), 0.0);
        assertEquals(0.0, GeoOperations.distance(polygon, point), 0.0);
    }

    /**
     * A geographic shape that holds another is at distance 0 from it, in either order, as the
     * planar model has it: the nearest edges and vertices of the two are the ring around the
     * shape inside, so the answer rests on the two being read as intersecting.
     */
    @Test
    public void shouldMeasureContainedGeographicShapesAsZero() {
        final Geometry square = Geometry.fromWkt("POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))", Model.GEOGRAPHIC);
        final Geometry inside = Geometry.fromWkt("POLYGON((0.2 0.2, 0.3 0.2, 0.3 0.3, 0.2 0.3, 0.2 0.2))",
                Model.GEOGRAPHIC);
        final Geometry across = Geometry.fromWkt("LINESTRING(0.5 -1, 0.5 2)", Model.GEOGRAPHIC);

        assertEquals(0.0, GeoOperations.distance(square, inside), 0.0);
        assertEquals(0.0, GeoOperations.distance(inside, square), 0.0);
        assertEquals(0.0, GeoOperations.distance(square, across), 0.0);
    }

    /**
     * A point beside a diagonal 47 km edge at 60 degrees north, where a degree of longitude covers
     * about half the ground a degree of latitude does. Weighing longitude as much as latitude
     * picks the wrong position on the edge and the distance comes out about 17% too far, so this
     * case pins the second nearest-points pass.
     */
    @Test
    public void shouldMeasureGeographicPointToLineStringAtHighLatitude() {
        final double[] edge = {0.0, 60.0, 0.6, 60.3};
        final double longitude = (edge[0] + edge[2]) / 2.0 - 0.06;
        final double latitude = (edge[1] + edge[3]) / 2.0 + 0.06;

        final Geometry point = Point.of(longitude, latitude, Model.GEOGRAPHIC);
        final Geometry line = lineString(edge);

        final double expected = sampledMinimumMeters(longitude, latitude, edge, 100_000);

        assertThat(relativeError(GeoOperations.distance(point, line), expected), lessThan(SHAPE_TOLERANCE));
        assertThat(relativeError(GeoOperations.distance(line, point), expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * A point beside a two edge line string at high latitude where shrinking longitude by the
     * cosine of the mean latitude ranks the far edge as the nearest one, so the scaled pass alone
     * would answer about 16% too far and only the nearer of the two passes is within the bar.
     */
    @Test
    public void shouldMeasureGeographicPointToLineStringAsTheNearerOfTheTwoPasses() {
        final double[] vertices = {-6.0, 76.0, -41.0, 74.0, -21.0, 68.0};
        final double longitude = -2.0;
        final double latitude = 70.0;

        final Geometry point = Point.of(longitude, latitude, Model.GEOGRAPHIC);
        final Geometry line = lineString(vertices);

        final double expected = sampledMinimumMeters(longitude, latitude, vertices, 100_000);

        assertThat(relativeError(GeoOperations.distance(point, line), expected), lessThan(SHAPE_TOLERANCE));
        assertThat(relativeError(GeoOperations.distance(line, point), expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * The north pole to an edge along 85 degrees north, where the cosine of the mean latitude
     * weighs longitude at a twentieth of latitude. Every position on the edge is 5 degrees of arc
     * from the pole, so the answer is that arc whichever position the passes land on.
     */
    @Test
    public void shouldMeasureGeographicPoleToLineString() {
        final double[] edge = {-20.0, 85.0, 20.0, 85.0};

        final Geometry pole = Geometry.fromWkt("POINT(0 90)", Model.GEOGRAPHIC);
        final Geometry line = lineString(edge);

        final double expected = sampledMinimumMeters(0.0, 90.0, edge, 100_000);
        final double measured = GeoOperations.distance(pole, line);

        assertThat(Double.isFinite(measured), is(true));
        assertThat(relativeError(measured, expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * A point to a multi line string, measured as the nearest of its parts, which here is the
     * second part rather than the first.
     */
    @Test
    public void shouldMeasureGeographicPointToMultiLineString() {
        final double[] near = {-1.0, 0.0, 1.0, 0.0};
        final double[] far = {-1.0, 5.0, 1.0, 5.0};

        final Geometry point = Geometry.fromWkt("POINT(0 1)", Model.GEOGRAPHIC);
        final Geometry lines = Geometry.fromWkt("MULTILINESTRING((-1 5, 1 5), (-1 0, 1 0))", Model.GEOGRAPHIC);

        final double expected = Math.min(sampledMinimumMeters(0.0, 1.0, near, 2_000),
                sampledMinimumMeters(0.0, 1.0, far, 2_000));

        assertThat(relativeError(GeoOperations.distance(point, lines), expected), lessThan(SHAPE_TOLERANCE));
        assertThat(relativeError(GeoOperations.distance(lines, point), expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * A collection with an empty part, which contributes no position, so the collection is
     * measured as the part that holds a coordinate.
     */
    @Test
    public void shouldMeasureGeographicCollectionWithEmptyPartAsItsOtherPart() {
        final Geometry collection = Geometry.fromWkt("GEOMETRYCOLLECTION(POINT EMPTY, POINT(1 1))", Model.GEOGRAPHIC);
        final Geometry probe = Geometry.fromWkt("POINT(1 3)", Model.GEOGRAPHIC);

        final double expected = haversineMeters(1.0, 1.0, 3.0, 1.0);

        assertEquals(expected, GeoOperations.distance(collection, probe), expected * 1e-6);
    }

    /**
     * Two disjoint squares one degree of latitude apart, whose nearest positions sit on the same
     * meridian, so the answer is that degree of latitude on any meridian the passes land on.
     */
    @Test
    public void shouldMeasureDisjointGeographicPolygonToPolygon() {
        final Geometry below = Geometry.fromWkt("POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))", Model.GEOGRAPHIC);
        final Geometry above = Geometry.fromWkt("POLYGON((0 2, 1 2, 1 3, 0 3, 0 2))", Model.GEOGRAPHIC);

        final double expected = haversineMeters(1.0, 0.0, 2.0, 0.0);

        assertThat(relativeError(GeoOperations.distance(below, above), expected), lessThan(SHAPE_TOLERANCE));
        assertThat(relativeError(GeoOperations.distance(above, below), expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * A z ordinate is dropped, so a 3D operand measures as its 2D position does. Both distances
     * are also checked against one degree of latitude by the test's own haversine, so the pair
     * cannot agree on a wrong answer.
     */
    @Test
    public void shouldIgnoreZ() {
        final Geometry pointWithZ = Geometry.fromWkt("POINT Z(0 0 1000)", Model.GEOGRAPHIC);
        final Geometry point = Geometry.fromWkt("POINT(0 0)", Model.GEOGRAPHIC);
        final Geometry probe = Geometry.fromWkt("POINT(0 1)", Model.GEOGRAPHIC);
        final Geometry line = Geometry.fromWkt("LINESTRING(-1 1, 1 1)", Model.GEOGRAPHIC);

        assertEquals(GeoOperations.distance(point, probe), GeoOperations.distance(pointWithZ, probe), 0.0);
        assertEquals(GeoOperations.distance(point, line), GeoOperations.distance(pointWithZ, line), 0.0);

        // the probe and the nearest position on the line both sit one degree of latitude away
        final double expected = haversineMeters(0.0, 0.0, 1.0, 0.0);
        assertEquals(expected, GeoOperations.distance(pointWithZ, probe), expected * 1e-6);
        assertThat(relativeError(GeoOperations.distance(pointWithZ, line), expected), lessThan(SHAPE_TOLERANCE));
    }

    /**
     * A few hundred random points beside random line strings of 3 to 8 edges of up to 100 km, all
     * within 75 degrees of latitude and 10 degrees of longitude so that no edge nears a pole or
     * the antimeridian, each measure within the 1% bar of the sampled minimum.
     */
    @Test
    public void shouldMeasureRandomGeographicPointToLineStringWithinOnePercent() {
        final Random random = new Random(20240607L);

        for (int caseIndex = 0; caseIndex < 300; caseIndex++) {
            final double[] vertices = randomLineString(random);
            final double[] offset = randomPositionBeside(random, vertices);

            final Geometry point = Point.of(offset[0], offset[1], Model.GEOGRAPHIC);
            final Geometry line = lineString(vertices);

            final double expected = sampledMinimumMeters(offset[0], offset[1], vertices, 2_000);
            final double measured = GeoOperations.distance(point, line);

            assertThat("case " + caseIndex + ": " + point + " to " + line + ", expected about " + expected,
                    relativeError(measured, expected), lessThan(SHAPE_TOLERANCE));
        }
    }

    // ------------------------------------------------------------------------
    // Argument checks
    // ------------------------------------------------------------------------

    @Test
    public void shouldRejectNullGeometry() {
        final Geometry point = Geometry.fromWkt("POINT(0 0)");

        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(null, point)).getMessage(),
                containsString("Geometry to measure from cannot be null"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(point, null)).getMessage(),
                containsString("Geometry to measure to cannot be null"));
    }

    @Test
    public void shouldRejectDifferentModels() {
        final Geometry planar = Geometry.fromWkt("POINT(0 0)", Model.CARTESIAN);
        final Geometry geographic = Geometry.fromWkt("POINT(0 0)", Model.GEOGRAPHIC);

        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(planar, geographic)).getMessage(),
                containsString("same model"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(geographic, planar)).getMessage(),
                containsString("same model"));
    }

    /**
     * Two SRIDs that are both {@link Model#OTHER}, so the models agree and only the SRIDs differ,
     * and the SRID of the first operand is the one reported.
     */
    @Test
    public void shouldRejectDifferentOtherSrids() {
        final Geometry webMercator = Geometry.fromWkt("POINT(0 0)", 3857);
        final Geometry utm = Geometry.fromWkt("POINT(0 0)", 32614);

        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(webMercator, utm)).getMessage(),
                containsString("not defined for SRID 3857"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(utm, webMercator)).getMessage(),
                containsString("not defined for SRID 32614"));
    }

    /**
     * An unsupported SRID is reported for either operand, ahead of the models it does not match.
     */
    @Test
    public void shouldRejectOtherModelPairedWithAModelItMeasures() {
        final Geometry planar = Geometry.fromWkt("POINT(0 0)", Model.CARTESIAN);
        final Geometry webMercator = Geometry.fromWkt("POINT(3 4)", 3857);

        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(planar, webMercator)).getMessage(),
                containsString("not defined for SRID 3857"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.distance(webMercator, planar)).getMessage(),
                containsString("not defined for SRID 3857"));
    }

    @Test
    public void shouldRejectEmptyGeometry() {
        final Geometry point = Geometry.fromWkt("POINT(0 0)");

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.distance(Point.empty(), point)).getMessage(),
                containsString("Geometry to measure from cannot be empty"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.distance(point, LineString.empty())).getMessage(),
                containsString("Geometry to measure to cannot be empty"));
    }

    // ------------------------------------------------------------------------
    // Buffer, CARTESIAN
    // ------------------------------------------------------------------------

    /**
     * A planar point buffered by 2 coordinate units, whose area is within 2% of the circle of
     * that radius. The JTS buffer draws a 32-gon, whose area falls 0.6% short of the circle it is
     * drawn on.
     */
    @Test
    public void shouldBufferPlanarPointAsADisc() {
        final Geometry buffered = GeoOperations.buffer(Geometry.fromWkt("POINT(0 0)"), 2.0);

        assertThat(buffered, instanceOf(Polygon.class));
        final double expected = Math.PI * 4.0;
        assertThat(relativeError(ringArea((Polygon) buffered), expected), lessThan(0.02));
    }

    /**
     * A planar square of side 10 buffered by -2, which JTS shrinks to the square of side 6 inside
     * it.
     */
    @Test
    public void shouldShrinkPlanarPolygonByNegativeDistance() {
        final Geometry square = Geometry.fromWkt("POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        final Geometry shrunken = GeoOperations.buffer(square, -2.0);

        assertThat(shrunken, instanceOf(Polygon.class));
        final double area = ringArea((Polygon) shrunken);
        assertThat(area, lessThan(100.0));
        assertEquals(36.0, area, 36.0 * 0.02);
    }

    /**
     * The same square shrunk by more than half its side, which leaves nothing.
     */
    @Test
    public void shouldShrinkPlanarPolygonToEmptyByLargeNegativeDistance() {
        final Geometry square = Geometry.fromWkt("POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");

        assertEquals(Polygon.empty(), GeoOperations.buffer(square, -10.0));
    }

    /**
     * A point and a line string have no area to grow from, so neither a zero nor a negative
     * distance leaves anything of them.
     */
    @Test
    public void shouldBufferPlanarPointAndLineStringToEmptyAtZeroOrBelow() {
        final Geometry point = Geometry.fromWkt("POINT(1 2)");
        final Geometry line = Geometry.fromWkt("LINESTRING(0 0, 2 1)");

        assertEquals(Polygon.empty(), GeoOperations.buffer(point, 0.0));
        assertEquals(Polygon.empty(), GeoOperations.buffer(point, -1.0));
        assertEquals(Polygon.empty(), GeoOperations.buffer(line, 0.0));
        assertEquals(Polygon.empty(), GeoOperations.buffer(line, -1.0));
    }

    /**
     * An empty geometry gives an empty polygon under its own SRID, in either model. A negative
     * distance on an empty geographic geometry is covered by
     * {@code shouldBufferAnEmptyGeographicGeometryByANegativeDistanceToAnEmptyPolygon}.
     */
    @Test
    public void shouldBufferEmptyGeometryToEmptyPolygonWithItsSrid() {
        assertEquals(Polygon.empty(), GeoOperations.buffer(Point.empty(), 5.0));
        assertEquals(Polygon.empty(Model.GEOGRAPHIC),
                GeoOperations.buffer(LineString.empty(Model.GEOGRAPHIC), 1000.0));
    }

    // ------------------------------------------------------------------------
    // Buffer, GEOGRAPHIC
    // ------------------------------------------------------------------------

    /**
     * A geographic point buffered by 1 km, every vertex of which lies within 1% of 1 km of the
     * centre by the test's own haversine. The ring is only required to hold enough positions for
     * its chords to stay inside that bar, not the exact number the cap is drawn with.
     */
    @Test
    public void shouldBufferGeographicPointAtTheGivenDistance() {
        final Geometry buffered = GeoOperations.buffer(Point.of(10.0, 45.0, Model.GEOGRAPHIC), 1000.0);

        assertThat(buffered, instanceOf(Polygon.class));
        final List<Point> ring = ((Polygon) buffered).exteriorRing().points();
        assertThat(ring.size(), greaterThanOrEqualTo(17));
        for (final Point vertex : ring) {
            final double measured = haversineMeters(45.0, 10.0, vertex.y(), vertex.x());
            assertThat("vertex " + vertex, relativeError(measured, 1000.0), lessThan(SHAPE_TOLERANCE));
        }
    }

    /**
     * A geographic line string along the equator buffered by 1 km, which holds a position 900 m
     * from the midpoint of its one edge and not one 1100 m away, both placed by the test's own
     * destination formula.
     */
    @Test
    public void shouldBufferGeographicLineStringAlongItsEdge() {
        final Geometry line = Geometry.fromWkt("LINESTRING(-0.1 0, 0.1 0)", Model.GEOGRAPHIC);
        final Geometry buffered = GeoOperations.buffer(line, 1000.0);

        final double[] inside = destination(0.0, 0.0, 900.0, 0.0);
        final double[] outside = destination(0.0, 0.0, 1100.0, 0.0);

        assertTrue("expected " + buffered + " to hold " + inside[0] + " " + inside[1],
                holds(buffered, inside));
        assertFalse("expected " + buffered + " not to hold " + outside[0] + " " + outside[1],
                holds(buffered, outside));
    }

    /**
     * A z ordinate is dropped and the SRID of the input is carried over, so a 3D geographic point
     * buffers to a 2D geographic polygon.
     */
    @Test
    public void shouldBufferGeographicPointWithZTo2D() {
        final Geometry buffered = GeoOperations.buffer(
                Geometry.fromWkt("POINT Z(10 45 1000)", Model.GEOGRAPHIC), 1000.0);

        assertEquals(4326, buffered.srid());
        assertThat(buffered.hasZ(), is(false));
    }

    /**
     * A geographic polygon with a hole 1 degree wide, buffered by 1 km, which holds a position
     * inside the polygon well away from either ring, does not hold the centre of the hole, and
     * holds a position 900 m inside the hole from its rim, placed by the test's own destination
     * formula.
     */
    @Test
    public void shouldBufferGeographicPolygonWithAHole() {
        final Geometry polygon = Geometry.fromWkt(
                "POLYGON((-1 -1, 1 -1, 1 1, -1 1, -1 -1), (-0.5 -0.5, 0.5 -0.5, 0.5 0.5, -0.5 0.5, -0.5 -0.5))",
                Model.GEOGRAPHIC);
        final Geometry buffered = GeoOperations.buffer(polygon, 1000.0);

        final double[] insidePolygon = {0.75, 0.0};
        final double[] holeCentre = {0.0, 0.0};
        final double[] insideHoleFromItsRim = destination(0.0, -0.5, 900.0, 0.0);

        assertTrue("expected " + buffered + " to hold the polygon's interior position "
                + insidePolygon[0] + " " + insidePolygon[1], holds(buffered, insidePolygon));
        assertFalse("expected " + buffered + " not to hold the hole's centre "
                + holeCentre[0] + " " + holeCentre[1], holds(buffered, holeCentre));
        assertTrue("expected " + buffered + " to hold " + insideHoleFromItsRim[0] + " "
                + insideHoleFromItsRim[1] + ", 900 m inside the hole from its rim",
                holds(buffered, insideHoleFromItsRim));
    }

    /**
     * A geographic multi line string buffered by 1 km, which holds a position 900 m from the
     * midpoint of the second part's edge, so every part of a collection is buffered and not only
     * the first.
     */
    @Test
    public void shouldBufferEveryPartOfAGeographicMultiLineString() {
        final Geometry lines = Geometry.fromWkt("MULTILINESTRING((-0.1 5, 0.1 5), (-0.1 0, 0.1 0))",
                Model.GEOGRAPHIC);
        final Geometry buffered = GeoOperations.buffer(lines, 1000.0);

        final double[] besideSecondPart = destination(0.0, 0.0, 900.0, 0.0);

        assertTrue("expected " + buffered + " to hold " + besideSecondPart[0] + " "
                + besideSecondPart[1], holds(buffered, besideSecondPart));
    }

    /**
     * A geographic buffer is built in longitude and latitude with no antimeridian handling, so a
     * line that crosses the antimeridian is buffered the long way round, as its edge is drawn: the
     * band spans the globe the other way and holds {@code POINT(0 0)}.
     */
    @Test
    public void shouldBufferAnAntimeridianGeographicLineTheLongWayRound() {
        final Geometry line = Geometry.fromWkt("LINESTRING(179 0, -179 0)", Model.GEOGRAPHIC);
        final Geometry buffered = GeoOperations.buffer(line, 1000.0);

        assertTrue("expected " + buffered + " to hold 0 0", holds(buffered, new double[]{0.0, 0.0}));
    }

    /**
     * A geographic distance of zero takes the JTS buffer of zero, as the planar model does, which
     * leaves nothing of a point.
     */
    @Test
    public void shouldBufferGeographicPointAtZeroToAnEmptyPolygon() {
        assertEquals(Polygon.empty(Model.GEOGRAPHIC),
                GeoOperations.buffer(Point.of(10.0, 45.0, Model.GEOGRAPHIC), 0.0));
    }

    // ------------------------------------------------------------------------
    // Buffer, argument checks
    // ------------------------------------------------------------------------

    @Test
    public void shouldRejectNullGeometryToBuffer() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.buffer(null, 1.0)).getMessage(),
                containsString("cannot be null"));
    }

    @Test
    public void shouldRejectNonFiniteBufferDistance() {
        final Geometry point = Geometry.fromWkt("POINT(0 0)");

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, Double.NaN)).getMessage(),
                containsString("must be a finite number"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, Double.POSITIVE_INFINITY)).getMessage(),
                containsString("must be a finite number"));
        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, Double.NEGATIVE_INFINITY)).getMessage(),
                containsString("must be a finite number"));
    }

    @Test
    public void shouldRejectOtherModelToBuffer() {
        final Geometry webMercator = Geometry.fromWkt("POINT(0 0)", 3857);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(webMercator, 1.0)).getMessage(),
                containsString("not defined for SRID 3857"));
    }

    /**
     * A model the buffer does not build is refused ahead of the empty geometry it is given for, so
     * an empty geometry in a system of its own is an error rather than an empty polygon.
     */
    @Test
    public void shouldRejectEmptyOtherModelGeometryToBuffer() {
        final Geometry webMercator = Geometry.fromWkt("POINT EMPTY", 3857);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(webMercator, 1.0)).getMessage(),
                containsString("not defined for SRID 3857"));
    }

    @Test
    public void shouldRejectNegativeGeographicBufferDistance() {
        final Geometry point = Point.of(0.0, 0.0, Model.GEOGRAPHIC);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, -1000.0)).getMessage(),
                containsString("cannot be negative"));
    }

    /**
     * A cap drawn around a position 0.01 degrees from the antimeridian reaches past it, and a ring
     * in longitude and latitude cannot be drawn across it.
     */
    @Test
    public void shouldRejectGeographicBufferCrossingTheAntimeridian() {
        final Geometry point = Point.of(179.99, 0.0, Model.GEOGRAPHIC);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, 5000.0)).getMessage(),
                containsString("crosses the antimeridian"));
    }

    /**
     * A cap drawn around a position 0.01 degrees from the north pole reaches past it.
     */
    @Test
    public void shouldRejectGeographicBufferReachingAPole() {
        final Geometry point = Point.of(0.0, 89.99, Model.GEOGRAPHIC);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.buffer(point, 5000.0)).getMessage(),
                containsString("reaches a pole"));
    }

    /**
     * A ring that crosses itself gives pieces JTS cannot union, which is reported as an error
     * naming the buffer and carrying what JTS threw in its message.
     */
    @Test
    public void shouldRejectGeographicBufferOfASelfIntersectingPolygon() {
        final Geometry bowtie = Geometry.fromWkt("POLYGON((0 0, 1 1, 1 0, 0 1, 0 0))", Model.GEOGRAPHIC);

        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeoOperations.buffer(bowtie, 1000.0));

        // which rings JTS cannot union is up to its overlay code: this bowtie fails on JTS 1.20.0,
        // the version the build resolves, and a later version may union it instead
        assertThat(e.getMessage(), containsString("could not union the pieces"));
        // no cause chained, so that a remote caller reading the root cause of the exception sees
        // the same text as an embedded caller
        assertNull(e.getCause());
    }

    /**
     * The ceiling on the points a buffer takes, checked with limits a test can reach rather than
     * by building geometries of ten and a hundred thousand points.
     */
    @Test
    public void shouldRejectMorePointsThanTheLimitAllows() {
        GeoOperations.checkBufferPointCount(3, 3);

        assertThat(assertThrows(IllegalArgumentException.class,
                        () -> GeoOperations.checkBufferPointCount(4, 3)).getMessage(),
                containsString("buffer() takes a geometry of at most 3 points, and this geometry holds 4"));
    }

    /**
     * A geographic line string of one point more than the geographic ceiling, refused by the
     * count of its points before any cap is drawn. A geographic buffer draws a cap per vertex and
     * joins the caps of every edge, so it takes the smaller input of the two models.
     */
    @Test
    public void shouldRejectAGeographicBufferOfTooManyPoints() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeoOperations.buffer(
                        lineStringOfPoints(GeoOperations.MAX_GEOGRAPHIC_BUFFER_POINTS + 1, Model.GEOGRAPHIC), 10.0));

        assertThat(e.getMessage(), containsString("buffer() takes a geometry of at most 10000 points, "
                + "and this geometry holds 10001"));
        // no cause chained, as above
        assertNull(e.getCause());
    }

    /**
     * A planar line string of one point more than the planar ceiling, which is the larger of the
     * two, since one JTS buffer does the whole job.
     */
    @Test
    public void shouldRejectAPlanarBufferOfTooManyPoints() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeoOperations.buffer(
                        lineStringOfPoints(GeoOperations.MAX_CARTESIAN_BUFFER_POINTS + 1, Model.CARTESIAN), 1.0));

        assertThat(e.getMessage(), containsString("buffer() takes a geometry of at most 100000 points, "
                + "and this geometry holds 100001"));
        assertNull(e.getCause());
    }

    /**
     * An empty geometry is answered with an empty polygon before the rule that a geographic
     * buffer does not take a negative distance, so the two together are not an error.
     */
    @Test
    public void shouldBufferAnEmptyGeographicGeometryByANegativeDistanceToAnEmptyPolygon() {
        assertEquals(Polygon.empty(Model.GEOGRAPHIC),
                GeoOperations.buffer(Point.empty(Model.GEOGRAPHIC), -1000.0));
        assertEquals(Polygon.empty(Model.GEOGRAPHIC),
                GeoOperations.buffer(Geometry.fromWkt("GEOMETRYCOLLECTION(POINT EMPTY)", Model.GEOGRAPHIC), -1000.0));
    }

    // ------------------------------------------------------------------------
    // Envelope
    // ------------------------------------------------------------------------

    /**
     * The box the JTS envelope draws, whose ring runs from the lower left corner up the left side,
     * as PostGIS {@code ST_Envelope} writes it too.
     */
    @Test
    public void shouldTakeTheEnvelopeOfALineStringAsABox() {
        assertEquals(Geometry.fromWkt("POLYGON((0 0, 0 1, 2 1, 2 0, 0 0))"),
                GeoOperations.envelope(Geometry.fromWkt("LINESTRING(0 0, 2 1)")));
    }

    @Test
    public void shouldTakeTheEnvelopeOfAPointAsThatPoint() {
        assertEquals(Point.of(1.0, 2.0), GeoOperations.envelope(Geometry.fromWkt("POINT(1 2)")));
    }

    /**
     * A box with no width is a line string, since it has no area to bound.
     */
    @Test
    public void shouldTakeTheEnvelopeOfAVerticalLineAsALineString() {
        final Geometry envelope = GeoOperations.envelope(Geometry.fromWkt("LINESTRING(1 0, 1 2)"));

        assertThat(envelope, instanceOf(LineString.class));
        assertEquals(Geometry.fromWkt("LINESTRING(1 0, 1 2)"), envelope);
    }

    @Test
    public void shouldTakeTheEnvelopeOfAnEmptyGeometryAsAnEmptyPoint() {
        assertEquals(Point.empty(), GeoOperations.envelope(Polygon.empty()));
        assertEquals(Point.empty(Model.GEOGRAPHIC),
                GeoOperations.envelope(LineString.empty(Model.GEOGRAPHIC)));
    }

    /**
     * A box is read off the coordinates, which asks nothing of the reference system they are given
     * in, so a geometry in a system of its own keeps that SRID.
     */
    @Test
    public void shouldTakeTheEnvelopeOfAnOtherModelGeometryUnderItsSrid() {
        final Geometry envelope = GeoOperations.envelope(Geometry.fromWkt("LINESTRING(0 0, 2 1)", 3857));

        assertEquals(3857, envelope.srid());
        assertEquals(Geometry.fromWkt("POLYGON((0 0, 0 1, 2 1, 2 0, 0 0))", 3857), envelope);
    }

    @Test
    public void shouldTakeTheEnvelopeOfAPointWithZAs2D() {
        final Geometry envelope = GeoOperations.envelope(Geometry.fromWkt("POINT Z(1 2 3)"));

        assertThat(envelope.hasZ(), is(false));
        assertEquals(Point.of(1.0, 2.0), envelope);
    }

    /**
     * A geographic box is computed in longitude and latitude with no antimeridian handling, so a
     * line that crosses the antimeridian is bounded the long way round.
     */
    @Test
    public void shouldTakeTheEnvelopeOfAnAntimeridianLineTheLongWayRound() {
        assertEquals(Geometry.fromWkt("LINESTRING(-179 0, 179 0)", Model.GEOGRAPHIC),
                GeoOperations.envelope(Geometry.fromWkt("LINESTRING(179 0, -179 0)", Model.GEOGRAPHIC)));
    }

    @Test
    public void shouldRejectNullGeometryToTakeTheEnvelopeOf() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> GeoOperations.envelope(null)).getMessage(),
                containsString("cannot be null"));
    }

    // ------------------------------------------------------------------------
    // The test's own math
    // ------------------------------------------------------------------------

    /**
     * Returns the error of a measure as a fraction of the expected value, the denominator floored
     * at 1 in the units of {@code expected}, so that a case whose expected value is a fraction of
     * a unit neither inflates the fraction nor divides by zero.
     */
    private static double relativeError(final double measured, final double expected) {
        return Math.abs(measured - expected) / Math.max(expected, 1.0);
    }

    /**
     * Returns the area of a polygon's exterior ring by the shoelace formula, in the square of the
     * coordinate units.
     */
    private static double ringArea(final Polygon polygon) {
        final List<Point> ring = polygon.exteriorRing().points();
        double twiceArea = 0.0;
        for (int vertex = 0; vertex + 1 < ring.size(); vertex++) {
            twiceArea += ring.get(vertex).x() * ring.get(vertex + 1).y()
                    - ring.get(vertex + 1).x() * ring.get(vertex).y();
        }
        return Math.abs(twiceArea) / 2.0;
    }

    /**
     * Returns the position {@code meters} from a position on the given bearing in degrees, by the
     * spherical direct formula, as a longitude and latitude pair in degrees.
     */
    private static double[] destination(final double longitude, final double latitude,
                                        final double meters, final double bearingDegrees) {
        final double arc = meters / RADIUS_METERS;
        final double bearing = Math.toRadians(bearingDegrees);
        final double startLatitude = Math.toRadians(latitude);

        final double endLatitude = Math.asin(Math.sin(startLatitude) * Math.cos(arc)
                + Math.cos(startLatitude) * Math.sin(arc) * Math.cos(bearing));
        final double endLongitude = Math.toRadians(longitude)
                + Math.atan2(Math.sin(bearing) * Math.sin(arc) * Math.cos(startLatitude),
                        Math.cos(arc) - Math.sin(startLatitude) * Math.sin(endLatitude));

        return new double[]{Math.toDegrees(endLongitude), Math.toDegrees(endLatitude)};
    }

    /**
     * Returns true if a geometry holds a position, read through JTS from the geometry's WKT so
     * that the answer owes nothing to the conversion the operations use.
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

    /**
     * Returns the haversine distance in meters between two positions in degrees.
     */
    private static double haversineMeters(final double latitude1, final double longitude1,
                                          final double latitude2, final double longitude2) {
        final double halfLatitude = Math.sin(Math.toRadians(latitude2 - latitude1) / 2.0);
        final double halfLongitude = Math.sin(Math.toRadians(longitude2 - longitude1) / 2.0);
        final double h = halfLatitude * halfLatitude
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * halfLongitude * halfLongitude;
        return 2.0 * Math.asin(Math.min(1.0, Math.sqrt(h))) * RADIUS_METERS;
    }

    /**
     * Returns the minimum haversine distance in meters from a position to a line string whose
     * edges are straight in longitude and latitude. Each edge is divided into {@code steps}
     * intervals and every division sampled, then the interval around the best sample is divided
     * and sampled again, which pins the minimum far below the 1% bar.
     *
     * @param longitude the position's longitude in degrees
     * @param latitude the position's latitude in degrees
     * @param vertices the line string's vertices, as longitude and latitude pairs in degrees
     * @param steps the number of intervals an edge is divided into, in each of the two passes
     */
    private static double sampledMinimumMeters(final double longitude, final double latitude,
                                               final double[] vertices, final int steps) {
        double best = Double.MAX_VALUE;
        for (int vertex = 0; vertex + 3 < vertices.length; vertex += 2) {
            double bestOnEdge = Double.MAX_VALUE;
            int bestStep = 0;
            for (int step = 0; step <= steps; step++) {
                final double sampled = sampleEdge(longitude, latitude, vertices, vertex, (double) step / steps);
                if (sampled < bestOnEdge) {
                    bestOnEdge = sampled;
                    bestStep = step;
                }
            }

            final double low = (double) Math.max(0, bestStep - 1) / steps;
            final double high = (double) Math.min(steps, bestStep + 1) / steps;
            for (int step = 0; step <= steps; step++) {
                final double sampled = sampleEdge(longitude, latitude, vertices, vertex,
                        low + (high - low) * step / steps);
                if (sampled < bestOnEdge) {
                    bestOnEdge = sampled;
                }
            }
            best = Math.min(best, bestOnEdge);
        }
        return best;
    }

    /**
     * Returns the distance in meters from a position to the point {@code fraction} of the way
     * along the edge that starts at {@code vertex}, interpolated in degrees.
     */
    private static double sampleEdge(final double longitude, final double latitude, final double[] vertices,
                                     final int vertex, final double fraction) {
        return haversineMeters(latitude, longitude,
                vertices[vertex + 1] + (vertices[vertex + 3] - vertices[vertex + 1]) * fraction,
                vertices[vertex] + (vertices[vertex + 2] - vertices[vertex]) * fraction);
    }

    // ------------------------------------------------------------------------
    // Random cases
    // ------------------------------------------------------------------------

    /**
     * Returns the vertices of a line string of 3 to 8 edges, each up to 100 km long, every vertex
     * inside the box the random cases stay in.
     */
    private static double[] randomLineString(final Random random) {
        final int edges = 3 + random.nextInt(6);
        final double[] vertices = new double[(edges + 1) * 2];
        vertices[0] = (random.nextDouble() * 2.0 - 1.0) * MAX_RANDOM_LONGITUDE;
        vertices[1] = (random.nextDouble() * 2.0 - 1.0) * MAX_RANDOM_LATITUDE;

        for (int vertex = 2; vertex < vertices.length; vertex += 2) {
            final double[] moved = positionAway(random, vertices[vertex - 2], vertices[vertex - 1],
                    random.nextDouble() * 100_000.0);
            vertices[vertex] = moved[0];
            vertices[vertex + 1] = moved[1];
        }
        return vertices;
    }

    /**
     * Returns a position 1 km to 200 km from a randomly chosen vertex of a line string.
     */
    private static double[] randomPositionBeside(final Random random, final double[] vertices) {
        final int vertex = random.nextInt(vertices.length / 2) * 2;
        return positionAway(random, vertices[vertex], vertices[vertex + 1],
                1_000.0 + random.nextDouble() * 199_000.0);
    }

    /**
     * Returns the position {@code meters} away from a position in a random direction that stays
     * inside the box the random cases stay in. Bearings are drawn until one lands inside; a move
     * along the meridian is the fallback, which leaves longitude alone and so always fits, since
     * the box spans every latitude but the 15 degrees nearest each pole.
     */
    private static double[] positionAway(final Random random, final double longitude, final double latitude,
                                         final double meters) {
        for (int attempt = 0; attempt < 64; attempt++) {
            final double[] moved = move(longitude, latitude, meters, random.nextDouble() * 2.0 * Math.PI);
            if (inBox(moved)) {
                return moved;
            }
        }
        final double[] north = move(longitude, latitude, meters, 0.0);
        return inBox(north) ? north : move(longitude, latitude, meters, Math.PI);
    }

    /**
     * Returns the position {@code meters} from a position on the given bearing in radians, taken
     * on the flat with a degree of longitude shortened by the cosine of the latitude, which is
     * close enough for placing a test case.
     */
    private static double[] move(final double longitude, final double latitude, final double meters,
                                 final double bearing) {
        final double degreesPerMeter = Math.toDegrees(1.0 / RADIUS_METERS);
        return new double[]{
                longitude + meters * Math.sin(bearing) * degreesPerMeter / Math.cos(Math.toRadians(latitude)),
                latitude + meters * Math.cos(bearing) * degreesPerMeter
        };
    }

    private static boolean inBox(final double[] position) {
        return Math.abs(position[0]) <= MAX_RANDOM_LONGITUDE && Math.abs(position[1]) <= MAX_RANDOM_LATITUDE;
    }

    /**
     * Builds a {@link Model#GEOGRAPHIC} line string from longitude and latitude pairs in degrees.
     */
    private static LineString lineString(final double[] vertices) {
        final Point[] points = new Point[vertices.length / 2];
        for (int ix = 0; ix < points.length; ix++) {
            points[ix] = Point.of(vertices[ix * 2], vertices[ix * 2 + 1], Model.GEOGRAPHIC);
        }
        return LineString.of(points);
    }

    /**
     * Builds a line string of {@code points} distinct positions, which climb a hundredth of a
     * degree at a time from the origin and so stay inside the longitude and latitude ranges for
     * the counts the buffer ceilings are tested with.
     */
    private static LineString lineStringOfPoints(final int points, final Model model) {
        final Point[] vertices = new Point[points];
        for (int vertex = 0; vertex < points; vertex++) {
            vertices[vertex] = Point.of(vertex % 100 * 0.01, vertex / 100 * 0.01, model);
        }
        return LineString.of(vertices);
    }
}
