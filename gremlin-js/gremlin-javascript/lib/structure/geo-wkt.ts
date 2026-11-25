/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

/**
 * Reader for the WKT and EWKT text of a geometry.
 *
 * It serves the translators, which turn a `Geometry(...)` literal into the
 * constructor chain that builds it, and the test glue, which turns a `geo[...]`
 * token into a value. The package exports no reader, so this module is not part
 * of the public API.
 */

import {
  Geometry,
  GeometryCollection,
  LineString,
  Model,
  ModelOrSrid,
  MultiLineString,
  MultiPoint,
  MultiPolygon,
  Point,
  Polygon,
} from './geo.js';

/** SRID of the `CARTESIAN` model. */
const SRID_CARTESIAN = 0;

/** SRID of the `GEOGRAPHIC` model, WGS84. */
const SRID_GEOGRAPHIC = 4326;

/** Deepest collection nesting a value may carry. */
const MAX_NESTING_DEPTH = 32;

/** Longest run of SRID digits accepted after an EWKT `SRID=` prefix. */
const MAX_SRID_DIGITS = 10;

/** Longest run of input echoed back in a message. */
const MAX_ECHOED_INPUT = 64;

/** Message for a coordinate that carries a third ordinate in text read under ISO rules. */
const MSG_UNTAGGED_THIRD_ORDINATE = 'Coordinate has 3 ordinates but no Z tag';

const INT32_MIN = -2147483648;
const INT32_MAX = 2147483647;

/**
 * Returns the SRID a model or SRID stands for.
 */
function sridFor(modelOrSrid: ModelOrSrid): number {
  if (typeof modelOrSrid === 'number') {
    if (!Number.isInteger(modelOrSrid) || modelOrSrid < INT32_MIN || modelOrSrid > INT32_MAX) {
      throw new Error(`SRID must be an integer in the signed 32-bit range, got: ${modelOrSrid}`);
    }
    return modelOrSrid;
  }
  if (modelOrSrid === Model.CARTESIAN) {
    return SRID_CARTESIAN;
  }
  if (modelOrSrid === Model.GEOGRAPHIC) {
    return SRID_GEOGRAPHIC;
  }
  throw new Error(`Model must be Model.CARTESIAN or Model.GEOGRAPHIC, or an SRID number, got: ${String(modelOrSrid)}`);
}

/**
 * Shortens a run of input for use in a message.
 */
function truncate(text: string): string {
  return text.length <= MAX_ECHOED_INPUT ? text : text.substring(0, MAX_ECHOED_INPUT) + '...';
}

function isDigit(c: string): boolean {
  return c >= '0' && c <= '9';
}

function isLetterOrDigit(c: string): boolean {
  return /[\p{L}\p{Nd}]/u.test(c);
}

/**
 * Reads one geometry from WKT or EWKT text.
 *
 * An `SRID=n;` prefix sets the SRID, and conflicts with a `modelOrSrid` that
 * says otherwise. The type names and the `Z` and `EMPTY` keywords are matched
 * without regard to case, and a multi-point admits both the parenthesized and
 * the bare form of a coordinate.
 *
 * ISO rules are applied first, and text that carries no `Z` tag at all is read
 * again as 3D when a coordinate holds a third ordinate, which is how PostGIS
 * EWKT writes a 3D value.
 *
 * @param text the WKT or EWKT text
 * @param modelOrSrid `Model.CARTESIAN`, `Model.GEOGRAPHIC` or an SRID, or
 *        `undefined` to take the SRID from an EWKT prefix and otherwise read the
 *        value as `CARTESIAN`
 * @throws {Error} if the text is empty, is malformed, carries the M or ZM
 *         dimension, or carries an SRID prefix that conflicts with
 *         `modelOrSrid`
 */
export function parseWkt(text: string, modelOrSrid?: ModelOrSrid): Geometry {
  if (text === null || text === undefined || text.trim().length === 0) {
    throw new Error('WKT input cannot be null or empty');
  }
  try {
    return new WktReader(text, false).read(modelOrSrid);
  } catch (err) {
    if (!(err instanceof UntaggedThirdOrdinate)) {
      throw err;
    }
    // the second pass reads every untagged part as 3D, so the third-ordinate signal cannot be
    // raised again and every error this call raises is a plain Error
    return new WktReader(text, true).read(modelOrSrid);
  }
}

/**
 * Signals that an untagged coordinate carries a third ordinate in text that
 * holds no `Z` tag, so the whole value can be read again as 3D. The signal
 * steers the second read and is never surfaced.
 */
class UntaggedThirdOrdinate extends Error {
  constructor() {
    super(MSG_UNTAGGED_THIRD_ORDINATE);
  }
}

/**
 * The reader of one geometry. One reader holds the position, the nesting depth,
 * the SRID and the z dimension of that geometry.
 */
class WktReader {
  #input: string;
  #pos = 0;
  #depth = 0;
  #srid = SRID_CARTESIAN;
  #hasZ: boolean | undefined = undefined;

  /** True when a part that carries no `Z` tag is read as 3D, the form PostGIS EWKT writes. */
  #readUntaggedAsZ: boolean;

  /** True when the text holds no `Z` tag at all, so it can be read again as 3D. */
  #zTagAbsent: boolean;

  constructor(input: string, readUntaggedAsZ: boolean) {
    this.#input = input;
    this.#readUntaggedAsZ = readUntaggedAsZ;
    this.#zTagAbsent = input.indexOf('Z') < 0 && input.indexOf('z') < 0;
  }

  read(modelOrSrid?: ModelOrSrid): Geometry {
    this.#skipWhitespace();

    let ewktSrid: number | undefined = undefined;
    if (this.#matchExact('SRID=')) {
      ewktSrid = this.#readSrid();
      this.#expect(';');
      this.#skipWhitespace();
    }

    if (ewktSrid !== undefined && modelOrSrid !== undefined) {
      const argumentSrid = sridFor(modelOrSrid);
      if (ewktSrid !== argumentSrid) {
        throw new Error(
          typeof modelOrSrid === 'number'
            ? `EWKT SRID ${ewktSrid} conflicts with argument SRID ${argumentSrid}`
            : `EWKT SRID ${ewktSrid} conflicts with model ${modelOrSrid}`,
        );
      }
    }

    if (modelOrSrid !== undefined) {
      this.#srid = sridFor(modelOrSrid);
    } else if (ewktSrid !== undefined) {
      this.#srid = ewktSrid;
    }

    const geometry = this.#readGeometry();

    this.#skipWhitespace();
    if (this.#pos < this.#input.length) {
      throw new Error(`Unexpected trailing text: ${truncate(this.#input.substring(this.#pos))}`);
    }
    return geometry;
  }

  #readGeometry(): Geometry {
    this.#skipWhitespace();

    if (this.#matchKeyword('POINT')) {
      return this.#readPoint();
    }
    if (this.#matchKeyword('LINESTRING')) {
      return this.#readLineString();
    }
    if (this.#matchKeyword('POLYGON')) {
      return this.#readPolygon();
    }
    if (this.#matchKeyword('MULTIPOINT')) {
      return this.#readMultiPoint();
    }
    if (this.#matchKeyword('MULTILINESTRING')) {
      return this.#readMultiLineString();
    }
    if (this.#matchKeyword('MULTIPOLYGON')) {
      return this.#readMultiPolygon();
    }
    if (this.#matchKeyword('GEOMETRYCOLLECTION')) {
      return this.#readGeometryCollection();
    }
    throw new Error(`Unknown geometry type at position ${this.#pos}`);
  }

  #readPoint(): Point {
    const z = this.#readZTag();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#recordZTag(z);
      return Point._emptyWithZ(this.#srid, z);
    }

    this.#expect('(');
    const point = this.#readCoordinate(z);
    this.#expect(')');

    this.#recordZTag(z);
    return point;
  }

  /**
   * Reads one coordinate, `x y` or `x y z`, as a point.
   */
  #readCoordinate(z: boolean): Point {
    this.#skipWhitespace();
    const x = this.#readNumber();
    this.#skipWhitespace();
    const y = this.#readNumber();
    this.#skipWhitespace();

    if (z) {
      if (this.#readUntaggedAsZ && !startsNumber(this.#peek())) {
        throw new Error('Value mixes 2D and 3D coordinates');
      }
      const zValue = this.#readNumber();
      this.#skipWhitespace();
      this.#rejectExtraOrdinates();
      return Point._ofWithSrid(x, y, zValue, this.#srid);
    }

    if (startsNumber(this.#peek())) {
      this.#readNumber();
      this.#skipWhitespace();
      this.#rejectExtraOrdinates();
      if (this.#zTagAbsent && !this.#readUntaggedAsZ) {
        // PostGIS EWKT writes a 3D value with no Z tag, so read the whole value again as 3D
        throw new UntaggedThirdOrdinate();
      }
      throw new Error(MSG_UNTAGGED_THIRD_ORDINATE);
    }
    return Point._ofWithSrid(x, y, undefined, this.#srid);
  }

  /**
   * Rejects a coordinate that carries a further ordinate where three have been
   * read, the XYZM form.
   */
  #rejectExtraOrdinates(): void {
    if (startsNumber(this.#peek())) {
      throw new Error('Coordinate has more than 3 ordinates but the M dimension is not supported');
    }
  }

  /**
   * Records the Z tag of a geometry, which must agree across one value.
   */
  #recordZTag(z: boolean): void {
    if (this.#hasZ === undefined) {
      this.#hasZ = z;
    } else if (this.#hasZ !== z) {
      throw new Error('Mixed Z tags in collection');
    }
  }

  #readLineString(): LineString {
    const z = this.#readZTag();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#recordZTag(z);
      return LineString._ofWithSrid([], this.#srid, z);
    }

    this.#expect('(');
    const points = this.#readCoordinateList(z);
    this.#expect(')');

    this.#recordZTag(z);
    return LineString._ofWithSrid(points, this.#srid, z);
  }

  #readPolygon(): Polygon {
    const z = this.#readZTag();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#recordZTag(z);
      return Polygon._ofWithSrid(LineString._ofWithSrid([], this.#srid, z), [], this.#srid, z);
    }

    this.#expect('(');
    const rings = this.#readRingList(z);
    this.#expect(')');

    this.#recordZTag(z);
    return this.#polygonFromRings(rings, z);
  }

  /**
   * Reads the ring list of a polygon body, after its opening parenthesis.
   */
  #readRingList(z: boolean): LineString[] {
    const rings: LineString[] = [];
    this.#skipWhitespace();
    for (;;) {
      this.#expect('(');
      rings.push(LineString._ofWithSrid(this.#readCoordinateList(z), this.#srid, z));
      this.#expect(')');
      this.#skipWhitespace();
      if (this.#peek() !== ',') {
        return rings;
      }
      this.#advance();
      this.#skipWhitespace();
    }
  }

  /**
   * Builds a polygon from a ring list, the first ring being the shell.
   */
  #polygonFromRings(rings: LineString[], z: boolean): Polygon {
    return Polygon._ofWithSrid(rings[0], rings.slice(1), this.#srid, z);
  }

  #readMultiPoint(): MultiPoint {
    const z = this.#enterCollection();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#depth--;
      this.#recordZTag(z);
      return MultiPoint._ofWithSrid([], this.#srid, z);
    }

    this.#expect('(');
    const points: Point[] = [];
    this.#skipWhitespace();
    for (;;) {
      // a part may be EMPTY, the parenthesized form "(0 0)" or the bare form "0 0"
      if (this.#matchKeyword('EMPTY')) {
        points.push(Point._emptyWithZ(this.#srid, z));
      } else if (this.#peek() === '(') {
        this.#advance();
        points.push(this.#readCoordinate(z));
        this.#expect(')');
      } else {
        points.push(this.#readCoordinate(z));
      }
      if (!this.#atPartSeparator()) {
        break;
      }
    }
    this.#expect(')');

    this.#depth--;
    this.#recordZTag(z);
    return MultiPoint._ofWithSrid(points, this.#srid, z);
  }

  #readMultiLineString(): MultiLineString {
    const z = this.#enterCollection();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#depth--;
      this.#recordZTag(z);
      return MultiLineString._ofWithSrid([], this.#srid, z);
    }

    this.#expect('(');
    const lineStrings: LineString[] = [];
    this.#skipWhitespace();
    for (;;) {
      if (this.#matchKeyword('EMPTY')) {
        lineStrings.push(LineString._ofWithSrid([], this.#srid, z));
      } else {
        this.#expect('(');
        lineStrings.push(LineString._ofWithSrid(this.#readCoordinateList(z), this.#srid, z));
        this.#expect(')');
      }
      if (!this.#atPartSeparator()) {
        break;
      }
    }
    this.#expect(')');

    this.#depth--;
    this.#recordZTag(z);
    return MultiLineString._ofWithSrid(lineStrings, this.#srid, z);
  }

  #readMultiPolygon(): MultiPolygon {
    const z = this.#enterCollection();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#depth--;
      this.#recordZTag(z);
      return MultiPolygon._ofWithSrid([], this.#srid, z);
    }

    this.#expect('(');
    const polygons: Polygon[] = [];
    this.#skipWhitespace();
    for (;;) {
      if (this.#matchKeyword('EMPTY')) {
        polygons.push(Polygon._ofWithSrid(LineString._ofWithSrid([], this.#srid, z), [], this.#srid, z));
      } else {
        this.#expect('(');
        polygons.push(this.#polygonFromRings(this.#readRingList(z), z));
        this.#expect(')');
      }
      if (!this.#atPartSeparator()) {
        break;
      }
    }
    this.#expect(')');

    this.#depth--;
    this.#recordZTag(z);
    return MultiPolygon._ofWithSrid(polygons, this.#srid, z);
  }

  #readGeometryCollection(): GeometryCollection {
    const z = this.#enterCollection();
    this.#skipWhitespace();

    if (this.#matchKeyword('EMPTY')) {
      this.#depth--;
      this.#recordZTag(z);
      return GeometryCollection._ofWithSrid([], this.#srid, z);
    }

    this.#expect('(');
    const geometries: Geometry[] = [];
    this.#skipWhitespace();
    for (;;) {
      geometries.push(this.#readGeometry());
      if (!this.#atPartSeparator()) {
        break;
      }
    }
    this.#expect(')');

    this.#depth--;

    // every part must carry the same Z tag as the collection
    for (const part of geometries) {
      if (part.hasZ !== z) {
        throw new Error('Mixed Z tags in collection');
      }
    }

    this.#recordZTag(z);
    return GeometryCollection._ofWithSrid(geometries, this.#srid, z);
  }

  /**
   * Counts one level of collection nesting and reads the Z tag of the
   * collection.
   */
  #enterCollection(): boolean {
    this.#depth++;
    if (this.#depth > MAX_NESTING_DEPTH) {
      throw new Error(`Collection nesting depth exceeds maximum of ${MAX_NESTING_DEPTH}`);
    }
    return this.#readZTag();
  }

  /**
   * Steps over the comma between two parts of a collection, and reports whether
   * one was there.
   */
  #atPartSeparator(): boolean {
    this.#skipWhitespace();
    if (this.#peek() !== ',') {
      return false;
    }
    this.#advance();
    this.#skipWhitespace();
    return true;
  }

  #readCoordinateList(z: boolean): Point[] {
    const points: Point[] = [];
    this.#skipWhitespace();
    for (;;) {
      points.push(this.#readCoordinate(z));
      if (!this.#atPartSeparator()) {
        return points;
      }
    }
  }

  #readZTag(): boolean {
    this.#skipWhitespace();
    if (this.#matchKeyword('ZM')) {
      throw new Error('ZM dimension not supported');
    }
    if (this.#matchKeyword('Z')) {
      this.#skipWhitespace();
      // a separated M tag means ZM
      if (this.#matchKeyword('M')) {
        throw new Error('ZM dimension not supported');
      }
      return true;
    }
    if (this.#matchKeyword('M')) {
      throw new Error('M dimension not supported');
    }
    return this.#readUntaggedAsZ;
  }

  #readNumber(): number {
    this.#skipWhitespace();
    const start = this.#pos;

    if (this.#matchKeyword('NaN')) {
      throw new Error('NaN is not allowed in WKT');
    }
    if (this.#matchKeyword('Infinity') || this.#matchKeyword('-Infinity') || this.#matchKeyword('+Infinity')) {
      throw new Error('Infinity is not allowed in WKT');
    }

    if (this.#peek() === '-' || this.#peek() === '+') {
      this.#advance();
    }
    while (isDigit(this.#peek())) {
      this.#advance();
    }
    if (this.#peek() === '.') {
      this.#advance();
      while (isDigit(this.#peek())) {
        this.#advance();
      }
    }
    if (this.#peek() === 'e' || this.#peek() === 'E') {
      this.#advance();
      if (this.#peek() === '-' || this.#peek() === '+') {
        this.#advance();
      }
      while (isDigit(this.#peek())) {
        this.#advance();
      }
    }

    if (start === this.#pos) {
      throw new Error(`Expected number at position ${this.#pos}`);
    }

    const text = this.#input.substring(start, this.#pos);
    const value = Number(text);
    if (Number.isNaN(value)) {
      throw new Error(`Invalid number: ${truncate(text)}`);
    }
    return value;
  }

  /**
   * Reads the SRID of an EWKT prefix. The digit run is bounded, so an
   * arbitrarily long run of digits is rejected rather than echoed or parsed.
   */
  #readSrid(): number {
    this.#skipWhitespace();
    const start = this.#pos;

    if (this.#peek() === '-' || this.#peek() === '+') {
      this.#advance();
    }

    const digitsStart = this.#pos;
    while (isDigit(this.#peek()) && this.#pos - digitsStart < MAX_SRID_DIGITS) {
      this.#advance();
    }

    if (this.#pos === digitsStart) {
      throw new Error(`Expected SRID digits at position ${digitsStart}`);
    }
    if (isDigit(this.#peek())) {
      throw new Error(`SRID has more than ${MAX_SRID_DIGITS} digits`);
    }

    const value = Number(this.#input.substring(start, this.#pos));
    if (value < INT32_MIN || value > INT32_MAX) {
      throw new Error('SRID is out of range for a 32-bit integer');
    }
    return value;
  }

  #expect(c: string): void {
    this.#skipWhitespace();
    if (this.#peek() !== c) {
      throw new Error(`Expected '${c}' at position ${this.#pos}`);
    }
    this.#advance();
  }

  #skipWhitespace(): void {
    while (this.#pos < this.#input.length && /\s/.test(this.#input.charAt(this.#pos))) {
      this.#pos++;
    }
  }

  /**
   * Matches a keyword without regard to case, and only where it is not the
   * start of a longer word.
   */
  #matchKeyword(keyword: string): boolean {
    if (!this.#matchesAt(keyword)) {
      return false;
    }
    const after = this.#pos + keyword.length;
    if (after < this.#input.length) {
      const next = this.#input.charAt(after);
      if (isLetterOrDigit(next) || next === '_') {
        return false;
      }
    }
    this.#pos = after;
    return true;
  }

  /**
   * Matches a prefix without regard to case, and without a word boundary.
   */
  #matchExact(prefix: string): boolean {
    if (!this.#matchesAt(prefix)) {
      return false;
    }
    this.#pos += prefix.length;
    return true;
  }

  #matchesAt(text: string): boolean {
    if (this.#pos + text.length > this.#input.length) {
      return false;
    }
    return this.#input.substring(this.#pos, this.#pos + text.length).toUpperCase() === text.toUpperCase();
  }

  #peek(): string {
    return this.#pos < this.#input.length ? this.#input.charAt(this.#pos) : '\0';
  }

  #advance(): void {
    if (this.#pos < this.#input.length) {
      this.#pos++;
    }
  }
}

/**
 * Returns whether a character can start a number.
 */
function startsNumber(c: string): boolean {
  return isDigit(c) || c === '-' || c === '+' || c === '.';
}
