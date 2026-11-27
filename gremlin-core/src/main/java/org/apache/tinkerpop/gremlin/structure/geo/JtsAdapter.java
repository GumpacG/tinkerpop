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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between a TinkerPop geometry and the JTS value that geometry operations are evaluated
 * with, such as {@link GeoPredicates} and {@link GeoOperations}. The two models hold the same
 * coordinates, so a geographic value is built in longitude and latitude and the model is applied
 * by the caller.
 * <p>
 * Only x and y are carried over in either direction: all geometry math is 2D, so z is dropped.
 * <p>
 * On the way to JTS an empty component of a multi kind or of a collection is skipped, so it
 * contributes nothing to the value the operation is evaluated with. A geometry that is empty
 * throughout never reaches here, because callers reject it or answer before converting.
 */
final class JtsAdapter {

    /**
     * The factory is immutable and carries the default floating precision model, so one instance
     * serves every thread.
     */
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private JtsAdapter() {
        // static methods only, do not instantiate class
    }

    /**
     * Converts a geometry of any kind, recursing into the parts of a collection.
     */
    static org.locationtech.jts.geom.Geometry toJts(final Geometry geometry) {
        if (geometry instanceof Point) {
            return toJts((Point) geometry);
        }
        if (geometry instanceof LineString) {
            return FACTORY.createLineString(coordinatesOf(((LineString) geometry).points()));
        }
        if (geometry instanceof Polygon) {
            return toJts((Polygon) geometry);
        }
        if (geometry instanceof MultiPoint) {
            final List<Point> points = nonEmpty(((MultiPoint) geometry).geometries());
            final org.locationtech.jts.geom.Point[] parts = new org.locationtech.jts.geom.Point[points.size()];
            for (int ix = 0; ix < parts.length; ix++) {
                parts[ix] = toJts(points.get(ix));
            }
            return FACTORY.createMultiPoint(parts);
        }
        if (geometry instanceof MultiLineString) {
            final List<LineString> lineStrings = nonEmpty(((MultiLineString) geometry).geometries());
            final org.locationtech.jts.geom.LineString[] parts = new org.locationtech.jts.geom.LineString[lineStrings.size()];
            for (int ix = 0; ix < parts.length; ix++) {
                parts[ix] = FACTORY.createLineString(coordinatesOf(lineStrings.get(ix).points()));
            }
            return FACTORY.createMultiLineString(parts);
        }
        if (geometry instanceof MultiPolygon) {
            final List<Polygon> polygons = nonEmpty(((MultiPolygon) geometry).geometries());
            final org.locationtech.jts.geom.Polygon[] parts = new org.locationtech.jts.geom.Polygon[polygons.size()];
            for (int ix = 0; ix < parts.length; ix++) {
                parts[ix] = toJts(polygons.get(ix));
            }
            return FACTORY.createMultiPolygon(parts);
        }
        if (geometry instanceof GeometryCollection) {
            final List<? extends Geometry> parts = nonEmpty(((GeometryCollection) geometry).geometries());
            final org.locationtech.jts.geom.Geometry[] elements = new org.locationtech.jts.geom.Geometry[parts.size()];
            for (int ix = 0; ix < elements.length; ix++) {
                elements[ix] = toJts(parts.get(ix));
            }
            return FACTORY.createGeometryCollection(elements);
        }
        throw new IllegalArgumentException("Unknown geometry kind " + geometry.getClass().getSimpleName());
    }

    /**
     * Returns the components that hold a coordinate, so that an empty one is left out of the value
     * it is a part of.
     */
    private static <G extends Geometry> List<G> nonEmpty(final List<G> components) {
        final List<G> kept = new ArrayList<>(components.size());
        for (final G component : components) {
            if (!component.isEmpty()) {
                kept.add(component);
            }
        }
        return kept;
    }

    private static org.locationtech.jts.geom.Point toJts(final Point point) {
        return point.isEmpty() ? FACTORY.createPoint() : FACTORY.createPoint(coordinateOf(point));
    }

    private static org.locationtech.jts.geom.Polygon toJts(final Polygon polygon) {
        final LineString shell = polygon.exteriorRing();
        if (shell.points().isEmpty()) {
            return FACTORY.createPolygon();
        }

        final List<LineString> interiorRings = polygon.interiorRings();
        final LinearRing[] holes = new LinearRing[interiorRings.size()];
        for (int ix = 0; ix < holes.length; ix++) {
            holes[ix] = FACTORY.createLinearRing(coordinatesOf(interiorRings.get(ix).points()));
        }
        return FACTORY.createPolygon(FACTORY.createLinearRing(coordinatesOf(shell.points())), holes);
    }

    private static Coordinate[] coordinatesOf(final List<Point> points) {
        final Coordinate[] coordinates = new Coordinate[points.size()];
        for (int ix = 0; ix < coordinates.length; ix++) {
            coordinates[ix] = coordinateOf(points.get(ix));
        }
        return coordinates;
    }

    private static Coordinate coordinateOf(final Point point) {
        return new Coordinate(point.x(), point.y());
    }

    // ------------------------------------------------------------------------
    // JTS to TinkerPop
    // ------------------------------------------------------------------------

    /**
     * Converts a JTS value of any kind back to a TinkerPop geometry under {@code srid},
     * recursing into the parts of a collection. The result is 2D, since a z a JTS coordinate may
     * carry is not read.
     *
     * @param srid the spatial reference ID the result carries, the caller's because a JTS value
     *         built by an operation carries no reference system of its own, so the coordinate
     *         checks of that SRID are applied here to the result of the operation
     * @return the geometry holding the same 2D coordinates
     * @throws IllegalArgumentException if {@code jts} is of a kind TinkerPop has no geometry for,
     *         or if a coordinate is outside the range {@code srid} allows
     */
    static Geometry fromJts(final org.locationtech.jts.geom.Geometry jts, final int srid) {
        if (jts instanceof org.locationtech.jts.geom.Point) {
            return pointFromJts((org.locationtech.jts.geom.Point) jts, srid);
        }
        // a linear ring is a line string, so this branch takes a polygon ring handed in on its own
        if (jts instanceof org.locationtech.jts.geom.LineString) {
            return lineStringFromJts((org.locationtech.jts.geom.LineString) jts, srid);
        }
        if (jts instanceof org.locationtech.jts.geom.Polygon) {
            return polygonFromJts((org.locationtech.jts.geom.Polygon) jts, srid);
        }
        // the three multi kinds are themselves collections, so each is matched before the general
        // collection it extends
        if (jts instanceof org.locationtech.jts.geom.MultiPoint) {
            final List<Point> points = new ArrayList<>(jts.getNumGeometries());
            for (int part = 0; part < jts.getNumGeometries(); part++) {
                points.add(pointFromJts((org.locationtech.jts.geom.Point) jts.getGeometryN(part), srid));
            }
            return MultiPoint.ofWithSrid(points, srid, false);
        }
        if (jts instanceof org.locationtech.jts.geom.MultiLineString) {
            final List<LineString> lineStrings = new ArrayList<>(jts.getNumGeometries());
            for (int part = 0; part < jts.getNumGeometries(); part++) {
                lineStrings.add(lineStringFromJts(
                        (org.locationtech.jts.geom.LineString) jts.getGeometryN(part), srid));
            }
            return MultiLineString.ofWithSrid(lineStrings, srid, false);
        }
        if (jts instanceof org.locationtech.jts.geom.MultiPolygon) {
            final List<Polygon> polygons = new ArrayList<>(jts.getNumGeometries());
            for (int part = 0; part < jts.getNumGeometries(); part++) {
                polygons.add(polygonFromJts((org.locationtech.jts.geom.Polygon) jts.getGeometryN(part), srid));
            }
            return MultiPolygon.ofWithSrid(polygons, srid, false);
        }
        if (jts instanceof org.locationtech.jts.geom.GeometryCollection) {
            final List<Geometry> parts = new ArrayList<>(jts.getNumGeometries());
            for (int part = 0; part < jts.getNumGeometries(); part++) {
                parts.add(fromJts(jts.getGeometryN(part), srid));
            }
            return GeometryCollection.createWithSrid(parts, srid, false);
        }
        throw new IllegalArgumentException("Unknown JTS geometry kind " + jts.getGeometryType());
    }

    private static Point pointFromJts(final org.locationtech.jts.geom.Point point, final int srid) {
        return point.isEmpty()
                ? Point.emptyWithZ(srid, false)
                : Point.ofWithSrid(point.getX(), point.getY(), srid);
    }

    private static LineString lineStringFromJts(final org.locationtech.jts.geom.LineString lineString,
                                                final int srid) {
        if (lineString.isEmpty()) {
            return LineString.emptyWithSrid(srid, false);
        }
        return LineString.ofWithSrid(pointsOf(lineString.getCoordinates(), srid), srid, false);
    }

    private static Polygon polygonFromJts(final org.locationtech.jts.geom.Polygon polygon, final int srid) {
        if (polygon.isEmpty()) {
            return Polygon.emptyWithSrid(srid, false);
        }
        final List<LineString> holes = new ArrayList<>(polygon.getNumInteriorRing());
        for (int ring = 0; ring < polygon.getNumInteriorRing(); ring++) {
            holes.add(lineStringFromJts(polygon.getInteriorRingN(ring), srid));
        }
        return Polygon.ofWithSrid(lineStringFromJts(polygon.getExteriorRing(), srid), holes, srid, false);
    }

    private static List<Point> pointsOf(final Coordinate[] coordinates, final int srid) {
        final List<Point> points = new ArrayList<>(coordinates.length);
        for (final Coordinate coordinate : coordinates) {
            points.add(Point.ofWithSrid(coordinate.x, coordinate.y, srid));
        }
        return points;
    }
}
