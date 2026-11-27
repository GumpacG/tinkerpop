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

import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.util.AssertionFailedException;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes distance, buffer and envelope on geometries, with JTS for {@link Model#CARTESIAN} and
 * the spherical formulas of Spatial4j for {@link Model#GEOGRAPHIC}.
 * <p>
 * All math is 2D, so z is ignored and every result is 2D.
 * <p>
 * Every call converts its own operands to JTS and caches nothing, so no mutable state is shared
 * between callers.
 *
 * @since 4.0.0
 */
public final class GeoOperations {

    // Spatial4j's mean radius of the earth, the IUGG arithmetic mean radius R1 (about 6,371,008.8 m),
    // on whose sphere every GEOGRAPHIC measure is taken.
    private static final double EARTH_MEAN_RADIUS_METERS = DistanceUtils.EARTH_MEAN_RADIUS_KM * 1000;

    /**
     * The number of positions on the circle of a {@link Model#GEOGRAPHIC} buffer cap. The chords
     * of a 32-gon lie inside the circle they are drawn on by about 0.5%, which is inside the 1%
     * accuracy bar the geographic operations hold to.
     */
    private static final int CAP_POSITIONS = 32;

    /**
     * The most points a geometry may hold to be buffered in the {@link Model#GEOGRAPHIC} model,
     * where every distinct vertex costs a cap of {@link #CAP_POSITIONS} positions and every edge
     * a convex hull, all of which are then unioned.
     */
    static final int MAX_GEOGRAPHIC_BUFFER_POINTS = 10_000;

    /**
     * The most points a geometry may hold to be buffered in the {@link Model#CARTESIAN} model,
     * where one JTS buffer does the whole job and so a larger geometry is still affordable.
     */
    static final int MAX_CARTESIAN_BUFFER_POINTS = 100_000;

    /**
     * The factory is immutable and carries the default floating precision model, so one instance
     * serves every thread.
     */
    private static final GeometryFactory FACTORY = new GeometryFactory();

    /**
     * Spatial4j only builds the position its bearing formula returns, which it does under the
     * world bounds of this context. The context is immutable, so one instance serves every thread.
     */
    private static final SpatialContext SPATIAL_CONTEXT = SpatialContext.GEO;

    // a value echoed in a message is cut to 64 characters plus "..."
    private static final int MAX_ECHOED_LENGTH = 67;

    private GeoOperations() {
        // static methods only, do not instantiate class
    }

    /**
     * Returns the minimum distance between two geometries, which is 0 when they intersect.
     * <p>
     * {@link Model#CARTESIAN} gives the planar distance in coordinate units.
     * <p>
     * {@link Model#GEOGRAPHIC} gives meters on a sphere of mean radius 6,371,008.8 meters.
     * Between two points it is the haversine distance. Between other shapes it is the haversine
     * distance of the nearest pair of positions found with JTS,
     * so edges are straight lines in longitude and latitude, as in {@link GeoPredicates}, and the
     * result is within about 1% for edges under about 100 km. The antimeridian is not handled, so
     * a shape that crosses it is measured the long way round.
     *
     * @param first the geometry to measure from
     * @param second the geometry to measure to
     * @return the distance, in coordinate units for {@link Model#CARTESIAN} and in meters for
     *         {@link Model#GEOGRAPHIC}
     * @throws IllegalArgumentException if either geometry is null or empty, if either model is
     *         {@link Model#OTHER}, which is a coordinate system TinkerPop does not define the math
     *         for, or if the two have different models
     * @since 4.0.0
     */
    public static double distance(final Geometry first, final Geometry second) {
        if (first == null) {
            throw new IllegalArgumentException("Geometry to measure from cannot be null");
        }
        if (second == null) {
            throw new IllegalArgumentException("Geometry to measure to cannot be null");
        }
        // a model this does not measure is reported for either operand, by the SRID that says so,
        // ahead of a model difference, which would name OTHER without saying which system it is
        if (first.model() == Model.OTHER || second.model() == Model.OTHER) {
            final int unsupported = first.model() == Model.OTHER ? first.srid() : second.srid();
            throw new IllegalArgumentException("Distance is not defined for SRID " + unsupported
                    + "; only the CARTESIAN and GEOGRAPHIC models are measured");
        }
        if (first.model() != second.model()) {
            throw new IllegalArgumentException("Distance needs both geometries in the same model, got "
                    + first.model() + " and " + second.model());
        }
        if (first.isEmpty()) {
            throw new IllegalArgumentException("Geometry to measure from cannot be empty");
        }
        if (second.isEmpty()) {
            throw new IllegalArgumentException("Geometry to measure to cannot be empty");
        }

        if (first.model() == Model.CARTESIAN) {
            return planarDistance(JtsAdapter.toJts(first), JtsAdapter.toJts(second));
        }

        if (first instanceof Point && second instanceof Point) {
            final Point from = (Point) first;
            final Point to = (Point) second;
            return haversineMeters(from.y(), from.x(), to.y(), to.x());
        }
        return geographicDistance(JtsAdapter.toJts(first), JtsAdapter.toJts(second));
    }

    /**
     * Returns the planar distance between two JTS values in coordinate units, which is 0 when
     * they intersect and otherwise the distance between their nearest facets.
     * <p>
     * Whether they intersect is asked first because a facet is a segment or a vertex of the
     * boundary, so a shape that holds the other would otherwise measure to that boundary rather
     * than to the interior it encloses. JTS answers the test cheaply, from the envelopes when they
     * do not overlap and from an indexed relate otherwise.
     */
    private static double planarDistance(final org.locationtech.jts.geom.Geometry from,
                                         final org.locationtech.jts.geom.Geometry to) {
        if (from.intersects(to)) {
            return 0.0;
        }
        return IndexedFacetDistance.distance(from, to);
    }

    /**
     * Returns the distance in meters between two shapes of which at least one is not a point,
     * taken as the haversine distance of the nearer of two pairs of positions on them.
     * <p>
     * The first pass works in raw longitude and latitude, where a degree of longitude counts as
     * much as a degree of latitude and so the pair sits too far east or west away from the
     * equator. The second pass finds the pair again with longitude scaled by the cosine of the
     * mean latitude of the first pair, which is the ground distance a degree of longitude covers
     * there. Both pairs are positions on the shapes, so each haversine distance is an upper bound
     * on the true minimum and the smaller of the two is the better answer. A pair whose mean
     * latitude is at a pole scales longitude by nearly zero, which weighs every meridian as one
     * position; the first pass still bounds the result.
     * <p>
     * Whether the two intersect is asked first, for the reason
     * {@link #planarDistance(org.locationtech.jts.geom.Geometry, org.locationtech.jts.geom.Geometry)}
     * gives, and because the scaled pass would otherwise be taken for a pair already known to be
     * the same position.
     */
    private static double geographicDistance(final org.locationtech.jts.geom.Geometry from,
                                             final org.locationtech.jts.geom.Geometry to) {
        if (from.intersects(to)) {
            return 0.0;
        }

        final Coordinate[] degreePair = IndexedFacetDistance.nearestPoints(from, to);
        final double degreeDistance = haversineMeters(degreePair[0].y, degreePair[0].x,
                degreePair[1].y, degreePair[1].x);
        if (degreeDistance == 0.0) {
            return 0.0;
        }

        final double meanLatitude = (degreePair[0].y + degreePair[1].y) / 2.0;
        final double scale = Math.cos(Math.toRadians(meanLatitude));

        final AffineTransformation shrinkLongitude = AffineTransformation.scaleInstance(scale, 1);
        final Coordinate[] scaledPair = IndexedFacetDistance.nearestPoints(
                shrinkLongitude.transform(from), shrinkLongitude.transform(to));

        final double scaledDistance = haversineMeters(scaledPair[0].y, scaledPair[0].x / scale,
                scaledPair[1].y, scaledPair[1].x / scale);

        return Math.min(degreeDistance, scaledDistance);
    }

    /**
     * Returns the haversine distance in meters between two positions given in degrees.
     */
    private static double haversineMeters(final double latitude1, final double longitude1,
                                         final double latitude2, final double longitude2) {
        final double radians = DistanceUtils.distHaversineRAD(
                Math.toRadians(latitude1), Math.toRadians(longitude1),
                Math.toRadians(latitude2), Math.toRadians(longitude2));
        return DistanceUtils.radians2Dist(radians, EARTH_MEAN_RADIUS_METERS);
    }

    // ------------------------------------------------------------------------
    // Buffer
    // ------------------------------------------------------------------------

    /**
     * Returns the region within {@code distance} of a geometry, in the model and the SRID of that
     * geometry and in 2D.
     * <p>
     * {@link Model#CARTESIAN} buffers in coordinate units with the JTS buffer under its default
     * parameters, so a zero or negative distance behaves as JTS defines it: a polygon shrinks and
     * may vanish to {@code POLYGON EMPTY}, and a point or a line string gives
     * {@code POLYGON EMPTY}. The rings of the result are the ones JTS 1.20 writes, in its
     * starting vertex and its clockwise direction, and the reference feature tests expect a
     * region in that form.
     * <p>
     * {@link Model#GEOGRAPHIC} buffers in meters on a sphere of mean radius 6,371,008.8 meters,
     * giving a {@link Polygon} or a {@link MultiPolygon} within about 1% of the true region. A
     * distance of zero gives the JTS buffer of zero, as
     * {@link Model#CARTESIAN} does. The region is built in longitude and latitude with no
     * antimeridian handling, so a geometry that crosses the antimeridian is buffered the long way
     * round: the buffer of {@code LINESTRING(179 0, -179 0)} is a band spanning the globe the
     * other way, which holds {@code POINT(0 0)}.
     * <p>
     * An empty geometry gives {@code POLYGON EMPTY} under its own SRID, which is answered before
     * any rule about the distance beyond its being a finite number, so an empty
     * {@link Model#GEOGRAPHIC} geometry and a negative distance give {@code POLYGON EMPTY} rather
     * than the error a geometry holding a coordinate would give.
     *
     * @param geometry the geometry to buffer
     * @param distance the buffer distance, in coordinate units for {@link Model#CARTESIAN} and in
     *                 meters for {@link Model#GEOGRAPHIC}
     * @return the buffered region
     * @throws IllegalArgumentException if {@code geometry} is null; if {@code distance} is
     *         {@code NaN} or infinite; if the model is {@link Model#OTHER}, which is a coordinate
     *         system TinkerPop does not define the math for; if the geometry holds more points
     *         than the model allows, {@value #MAX_GEOGRAPHIC_BUFFER_POINTS} for
     *         {@link Model#GEOGRAPHIC} and {@value #MAX_CARTESIAN_BUFFER_POINTS} for
     *         {@link Model#CARTESIAN}; if the model is {@link Model#GEOGRAPHIC} and
     *         {@code distance} is negative, since a geodesic buffer does not shrink a shape; if
     *         the model is {@link Model#GEOGRAPHIC} and a cap would cross the antimeridian or
     *         reach a pole, neither of which the union in longitude and latitude represents; or if
     *         JTS cannot build the region, which an invalid geometry can cause
     * @since 4.0.0
     */
    public static Geometry buffer(final Geometry geometry, final double distance) {
        if (geometry == null) {
            throw new IllegalArgumentException("Geometry to buffer cannot be null");
        }
        validateBufferDistance(distance);
        if (geometry.model() == Model.OTHER) {
            throw new IllegalArgumentException("Buffer is not defined for SRID " + geometry.srid()
                    + "; only the CARTESIAN and GEOGRAPHIC models are buffered");
        }
        if (geometry.isEmpty()) {
            return Polygon.emptyWithSrid(geometry.srid(), false);
        }

        final org.locationtech.jts.geom.Geometry jts = JtsAdapter.toJts(geometry);
        final boolean planar = geometry.model() == Model.CARTESIAN;
        checkBufferPointCount(jts.getNumPoints(),
                planar ? MAX_CARTESIAN_BUFFER_POINTS : MAX_GEOGRAPHIC_BUFFER_POINTS);

        if (planar || distance == 0.0) {
            return JtsAdapter.fromJts(planarBuffer(jts, distance), geometry.srid());
        }
        if (distance < 0.0) {
            throw new IllegalArgumentException("Buffer distance in the GEOGRAPHIC model cannot be negative, got "
                    + distance + " meters; a geodesic buffer only grows a shape");
        }
        return JtsAdapter.fromJts(geodesicBuffer(jts, distance), geometry.srid());
    }

    /**
     * Throws if a buffer distance is not a finite number, so that
     * {@link #buffer(Geometry, double)} and the step that calls it refuse such a distance with the
     * same message.
     *
     * @throws IllegalArgumentException if {@code distance} is {@code NaN} or infinite
     * @since 4.0.0
     */
    public static void validateBufferDistance(final double distance) {
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("Buffer distance must be a finite number, got " + distance);
        }
    }

    /**
     * Throws if a geometry holds more points than a buffer in its model takes. The limit is a
     * parameter so that the ceiling can be tested with a count a test can reach;
     * {@link #buffer(Geometry, double)} always passes the limit of the model it is buffering in,
     * {@link #MAX_GEOGRAPHIC_BUFFER_POINTS} or {@link #MAX_CARTESIAN_BUFFER_POINTS}.
     */
    static void checkBufferPointCount(final int points, final int limit) {
        if (points > limit) {
            throw new IllegalArgumentException("buffer() takes a geometry of at most " + limit
                    + " points, and this geometry holds " + points);
        }
    }

    /**
     * Returns the JTS buffer of a value, which is the whole of a {@link Model#CARTESIAN} buffer
     * and the answer a {@link Model#GEOGRAPHIC} buffer of zero gives.
     */
    private static org.locationtech.jts.geom.Geometry planarBuffer(
            final org.locationtech.jts.geom.Geometry jts, final double distance) {
        try {
            return jts.buffer(distance);
        } catch (TopologyException | AssertionFailedException e) {
            // what JTS threw is carried in the text and not chained as the cause, since Gremlin
            // Server reports only the root cause of an exception and a remote caller would then
            // read the JTS message alone, without the mention of the buffer
            throw new IllegalArgumentException("Buffer could not be drawn around this geometry: "
                    + StringUtils.abbreviate(e.getMessage(), MAX_ECHOED_LENGTH));
        }
    }

    /**
     * Returns the union, in longitude and latitude, of the pieces that make up a geodesic buffer:
     * a cap around every distinct vertex, the convex hull of the two caps of every edge, and the
     * polygonal parts of the input itself, which fill the interior a ring of edge pieces encloses.
     * A geometry that contributes no piece gives an empty polygon.
     */
    private static org.locationtech.jts.geom.Geometry geodesicBuffer(
            final org.locationtech.jts.geom.Geometry jts, final double meters) {
        final double radians = meters / EARTH_MEAN_RADIUS_METERS;

        final Map<Coordinate, org.locationtech.jts.geom.Polygon> caps = new LinkedHashMap<>();
        for (final Coordinate vertex : jts.getCoordinates()) {
            if (!caps.containsKey(vertex)) {
                caps.put(vertex, cap(vertex, radians, meters));
            }
        }

        final List<org.locationtech.jts.geom.Geometry> pieces = new ArrayList<>(caps.values());
        collectPieces(jts, caps, pieces);
        if (pieces.isEmpty()) {
            return FACTORY.createPolygon();
        }
        try {
            return UnaryUnionOp.union(pieces);
        } catch (TopologyException | AssertionFailedException e) {
            // no cause chained, for the reason planarBuffer() gives
            throw new IllegalArgumentException("Buffer in the GEOGRAPHIC model could not union the pieces it "
                    + "draws around this geometry: " + StringUtils.abbreviate(e.getMessage(), MAX_ECHOED_LENGTH));
        }
    }

    /**
     * Returns the polygon through {@link #CAP_POSITIONS} positions each exactly {@code radians}
     * of arc from {@code centre}, drawn in longitude and latitude.
     *
     * @throws IllegalArgumentException if the cap reaches a pole, which a ring in longitude and
     *         latitude cannot enclose, or crosses the antimeridian, where such a ring would be
     *         read as spanning the globe the other way
     */
    private static org.locationtech.jts.geom.Polygon cap(final Coordinate centre, final double radians,
                                                         final double meters) {
        final double degrees = Math.toDegrees(radians);
        if (Math.abs(centre.y) + degrees >= 90.0) {
            throw new IllegalArgumentException("Buffer of " + meters + " meters around the position "
                    + centre.x + " " + centre.y + " reaches a pole, which a buffer in longitude and "
                    + "latitude does not represent");
        }

        final double centreLatitudeRadians = Math.toRadians(centre.y);
        final double centreLongitudeRadians = Math.toRadians(centre.x);

        final Coordinate[] ring = new Coordinate[CAP_POSITIONS + 1];
        for (int position = 0; position < CAP_POSITIONS; position++) {
            final double bearing = 2.0 * Math.PI * position / CAP_POSITIONS;
            final org.locationtech.spatial4j.shape.Point onCircle = DistanceUtils.pointOnBearingRAD(
                    centreLatitudeRadians, centreLongitudeRadians, radians, bearing, SPATIAL_CONTEXT, null);

            final double longitude = Math.toDegrees(onCircle.getX());
            final double latitude = Math.toDegrees(onCircle.getY());
            // Spatial4j wraps a longitude past the antimeridian back into -180 to 180, which puts
            // the position a whole turn away from the centre it was measured from, so a crossing
            // shows up as a longitude difference no cap of this size could otherwise have
            if (Math.abs(longitude - centre.x) > 180.0) {
                throw new IllegalArgumentException("Buffer of " + meters + " meters around the position "
                        + centre.x + " " + centre.y + " crosses the antimeridian, which a buffer in "
                        + "longitude and latitude does not represent");
            }
            ring[position] = new Coordinate(longitude, latitude);
        }
        ring[CAP_POSITIONS] = new Coordinate(ring[0]);
        return FACTORY.createPolygon(ring);
    }

    /**
     * Adds to {@code pieces} the convex hull of the two caps of every edge of a line string or a
     * polygon ring, and every polygonal part of the geometry. A point contributes nothing beyond
     * the cap already drawn around it.
     */
    private static void collectPieces(final org.locationtech.jts.geom.Geometry jts,
                                      final Map<Coordinate, org.locationtech.jts.geom.Polygon> caps,
                                      final List<org.locationtech.jts.geom.Geometry> pieces) {
        if (jts instanceof org.locationtech.jts.geom.Polygon) {
            final org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) jts;
            if (polygon.isEmpty()) {
                return;
            }
            pieces.add(polygon);
            collectEdges(polygon.getExteriorRing(), caps, pieces);
            for (int ring = 0; ring < polygon.getNumInteriorRing(); ring++) {
                collectEdges(polygon.getInteriorRingN(ring), caps, pieces);
            }
            return;
        }
        if (jts instanceof org.locationtech.jts.geom.LineString) {
            collectEdges((org.locationtech.jts.geom.LineString) jts, caps, pieces);
            return;
        }
        if (jts instanceof org.locationtech.jts.geom.GeometryCollection) {
            for (int part = 0; part < jts.getNumGeometries(); part++) {
                collectPieces(jts.getGeometryN(part), caps, pieces);
            }
        }
    }

    /**
     * Adds to {@code pieces} the convex hull of the caps at the two ends of each edge, which is
     * the band reaching the buffer distance on either side of that edge, with round ends.
     */
    private static void collectEdges(final org.locationtech.jts.geom.LineString lineString,
                                     final Map<Coordinate, org.locationtech.jts.geom.Polygon> caps,
                                     final List<org.locationtech.jts.geom.Geometry> pieces) {
        final Coordinate[] vertices = lineString.getCoordinates();
        for (int edge = 0; edge + 1 < vertices.length; edge++) {
            if (vertices[edge].equals2D(vertices[edge + 1])) {
                continue;
            }
            pieces.add(FACTORY.createGeometryCollection(new org.locationtech.jts.geom.Geometry[]{
                    caps.get(vertices[edge]), caps.get(vertices[edge + 1])}).convexHull());
        }
    }

    // ------------------------------------------------------------------------
    // Envelope
    // ------------------------------------------------------------------------

    /**
     * Returns the 2D bounding box of a geometry, in the model and the SRID of that geometry.
     * <p>
     * The box is the JTS envelope: a {@link Polygon} of its four corners, or a {@link LineString}
     * when it has no width or no height, or a {@link Point} when the geometry is a single
     * position. An empty geometry gives {@code POINT EMPTY} under its own SRID. The ring of a
     * {@link Polygon} box is the one JTS 1.20 writes, beginning at the lower left corner and
     * running clockwise, and the reference feature tests expect a box in that form.
     * <p>
     * Any model is accepted, including {@link Model#OTHER}, since the box is read off the
     * coordinates and asks nothing of the reference system they are given in. A
     * {@link Model#GEOGRAPHIC} box is computed in longitude and latitude with no antimeridian
     * handling, so a geometry that crosses the antimeridian gives a box spanning the globe the
     * other way: {@code LINESTRING(179 0, -179 0)} gives a box from -179 to 179.
     *
     * @param geometry the geometry to take the bounding box of
     * @return the bounding box
     * @throws IllegalArgumentException if {@code geometry} is null
     * @since 4.0.0
     */
    public static Geometry envelope(final Geometry geometry) {
        if (geometry == null) {
            throw new IllegalArgumentException("Geometry to take the envelope of cannot be null");
        }
        if (geometry.isEmpty()) {
            return Point.emptyWithZ(geometry.srid(), false);
        }
        return JtsAdapter.fromJts(JtsAdapter.toJts(geometry).getEnvelope(), geometry.srid());
    }
}
