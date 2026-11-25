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

import assert from 'assert';
import { parseWkt } from '../../lib/structure/geo-wkt.js';
import { Model, MultiPoint, Point } from '../../lib/structure/geo.js';

describe('parseWkt', () => {
  describe('round trips', () => {
    const wkts = [
      'POINT(1 2)',
      'POINT(-0 0)',
      'POINT(0.0000001 2)',
      'POINT Z (1 2 3)',
      'POINT EMPTY',
      'POINT Z EMPTY',
      'LINESTRING(0 0, 1 1)',
      'LINESTRING EMPTY',
      'POLYGON((0 0, 4 0, 4 4, 0 4, 0 0))',
      'POLYGON((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 1 2, 2 2, 2 1, 1 1))',
      'POLYGON EMPTY',
      'MULTIPOINT((1 1), (2 2))',
      'MULTIPOINT(EMPTY, (2 2))',
      'MULTILINESTRING((0 0, 1 1), (2 2, 3 3))',
      'MULTILINESTRING(EMPTY)',
      'MULTIPOLYGON(((0 0, 1 0, 1 1, 0 0)))',
      'GEOMETRYCOLLECTION(POINT(1 1), LINESTRING(2 2, 3 3))',
      'GEOMETRYCOLLECTION(POINT(1 1), GEOMETRYCOLLECTION(MULTIPOINT EMPTY))',
      'GEOMETRYCOLLECTION EMPTY',
    ];

    wkts.forEach((wkt) => {
      it(`reads and writes ${wkt}`, () => {
        assert.strictEqual(parseWkt(wkt).toWKT(), wkt);
      });
    });
  });

  // the round trip above shows the text comes back unchanged; this pins the number behind it
  it('reads a small coordinate as its value', () => {
    assert.strictEqual(parseWkt('POINT(0.0000001 2)').x, 1e-7);
  });

  it('reads the bare coordinate form of a multi-point', () => {
    assert.ok(parseWkt('MULTIPOINT(1 1, 2 2)').equals(MultiPoint.of(Point.of(1, 1), Point.of(2, 2))));
  });

  describe('untagged 3D values', () => {
    // PostGIS EWKT writes a 3D value with no Z tag, which reads as 3D and is written back in the
    // ISO form
    const pairs = [
      ['POINT(1 2 3)', 'POINT Z (1 2 3)'],
      ['SRID=4326;LINESTRING(0 0 0,1 1 0,1 2 1)', 'SRID=4326;LINESTRING Z (0 0 0,1 1 0,1 2 1)'],
      [
        'POLYGON((0 0 0, 10 0 0, 10 10 0, 0 0 0), (2 2 1, 3 2 1, 3 3 1, 2 2 1))',
        'POLYGON Z ((0 0 0, 10 0 0, 10 10 0, 0 0 0), (2 2 1, 3 2 1, 3 3 1, 2 2 1))',
      ],
      ['MULTIPOINT(EMPTY,(1 1 1))', 'MULTIPOINT Z (EMPTY,(1 1 1))'],
      ['GEOMETRYCOLLECTION(POINT(1 2 3),POINT EMPTY)', 'GEOMETRYCOLLECTION Z (POINT Z (1 2 3),POINT Z EMPTY)'],
      ['MULTILINESTRING(EMPTY,(0 0 0, 1 1 1))', 'MULTILINESTRING Z (EMPTY,(0 0 0, 1 1 1))'],
    ];

    pairs.forEach(([untagged, tagged]) => {
      it(`reads ${untagged} as 3D`, () => {
        const geometry = parseWkt(untagged);
        assert.strictEqual(geometry.hasZ, true);
        assert.ok(geometry.equals(parseWkt(tagged)));
        assert.strictEqual(geometry.toWKT(), parseWkt(tagged).toWKT());
      });
    });

    it('rejects coordinates that mix 2 and 3 ordinates', () => {
      [
        'LINESTRING(0 0, 1 1 1)',
        'LINESTRING(0 0 0, 1 1)',
        'MULTIPOINT((0 0 0), (1 1))',
        'GEOMETRYCOLLECTION(POINT(1 2 3), POINT(4 5))',
        'POLYGON((0 0 0, 1 0 0, 1 1, 0 0 0))',
      ].forEach((wkt) => {
        assert.throws(() => parseWkt(wkt), /Value mixes 2D and 3D coordinates/, wkt);
      });
    });

    it('follows the ISO rules where the text carries a Z tag', () => {
      // an untagged part of tagged text names its missing Z tag, not a mix of dimensions
      assert.throws(
        () => parseWkt('GEOMETRYCOLLECTION(POINT Z (1 2 3), POINT(4 5 6))'),
        /Coordinate has 3 ordinates but no Z tag/,
      );
      // a tagged part short of its z ordinate names the missing number
      assert.throws(() => parseWkt('LINESTRING Z (0 0 0, 1 1)'), /Expected number/);
    });

    it('reports a failure found on the second reading as a plain error', () => {
      // the 2D forms of these three errors are covered below; here the reader has already
      // re-read the text as 3D when it fails
      assert.throws(() => parseWkt('POINT(1 2 3) extra'), /Unexpected trailing text: extra/);
      assert.throws(() => parseWkt('LINESTRING(0 0 0)'), /at least 2 points/);
      assert.throws(() => parseWkt('POINT(0 0 0 0 0)'), /more than 3 ordinates/);
    });
  });

  it('reads the type names and keywords without regard to case', () => {
    assert.ok(parseWkt('point z empty').equals(parseWkt('POINT Z EMPTY')));
    assert.ok(parseWkt('MultiPoint((1 1))').equals(MultiPoint.of(Point.of(1, 1))));
  });

  it('reads an SRID from an EWKT prefix', () => {
    assert.strictEqual(parseWkt('SRID=4326;POINT(1 2)').srid, 4326);
    assert.strictEqual(parseWkt('SRID=32618;POINT(1 2)').model, Model.OTHER);
  });

  it('takes the SRID from the model or SRID argument', () => {
    assert.strictEqual(parseWkt('POINT(1 2)').srid, 0);
    assert.strictEqual(parseWkt('POINT(1 2)', Model.GEOGRAPHIC).srid, 4326);
    assert.strictEqual(parseWkt('POINT(1 2)', 32618).srid, 32618);
  });

  it('rejects an EWKT prefix that conflicts with the argument', () => {
    assert.throws(
      () => parseWkt('SRID=4326;POINT(1 2)', Model.CARTESIAN),
      /EWKT SRID 4326 conflicts with model CARTESIAN/,
    );
    assert.throws(() => parseWkt('SRID=4326;POINT(1 2)', 32618), /EWKT SRID 4326 conflicts with argument SRID 32618/);
  });

  it('rejects text that is not a geometry', () => {
    assert.throws(() => parseWkt(''), /WKT input cannot be null or empty/);
    // WktReader.java answers the eight SQL/MM curve and surface types with "Unsupported geometry
    // type"; this reader does not name them, so they read as an unknown type
    assert.throws(() => parseWkt('CIRCULARSTRING(1 1, 2 2, 3 3)'), /Unknown geometry type/);
    // the 3D forms of the next three errors appear in the untagged 3D block above
    assert.throws(() => parseWkt('POINT(1)'), /Expected number/);
    assert.throws(() => parseWkt('POINT Z (1 2 3 4)'), /more than 3 ordinates/);
    assert.throws(() => parseWkt('POINT(1 2) extra'), /Unexpected trailing text: extra/);
    assert.throws(() => parseWkt('POINT M (1 2 3)'), /M dimension not supported/);
    assert.throws(() => parseWkt('POINT ZM (1 2 3 4)'), /ZM dimension not supported/);
    assert.throws(() => parseWkt('POINT(NaN 2)'), /NaN is not allowed in WKT/);
    assert.throws(() => parseWkt('GEOMETRYCOLLECTION(POINT Z (1 2 3), POINT(1 2))'), /Mixed Z tags in collection/);
    assert.throws(() => parseWkt('POLYGON((0 0, 1 0, 1 1, 0 1))'), /must be closed/);
    assert.throws(() => parseWkt('POLYGON((0 0, 1 0, 1 1))'), /Shell ring must have at least 4 points/);
    assert.throws(() => parseWkt('SRID=12345678901;POINT(1 2)'), /SRID has more than 10 digits/);
  });

  it('rejects collection nesting beyond the limit', () => {
    const open = 'GEOMETRYCOLLECTION('.repeat(33);
    assert.throws(
      () => parseWkt(open + 'POINT(1 1)' + ')'.repeat(33)),
      /Collection nesting depth exceeds maximum of 32/,
    );
  });
});
