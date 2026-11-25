#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

"""
A reader for the geometry text that the ``geo[...]`` token of a feature file holds. The driver
offers no WKT reader, so the test suite carries one of its own, covering the forms the features
use.
"""

import re

from gremlin_python.structure.geo import (GeometryCollection, LineString, MultiLineString,
                                          MultiPoint, MultiPolygon, Point, Polygon)
from radish import pick

_KINDS = {
    'POINT': Point,
    'LINESTRING': LineString,
    'POLYGON': Polygon,
    'MULTIPOINT': MultiPoint,
    'MULTILINESTRING': MultiLineString,
    'MULTIPOLYGON': MultiPolygon,
    'GEOMETRYCOLLECTION': GeometryCollection
}

# the element kind that each multi kind holds
_ELEMENTS = {
    MultiPoint: Point,
    MultiLineString: LineString,
    MultiPolygon: Polygon
}

_NAME = re.compile(r'[A-Za-z]+')
_NUMBER = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')


@pick
def parse_geometry(text):
    """
    Parses ISO WKT, or EWKT carrying an "SRID=n;" prefix, into a geometry value. Without a prefix
    the SRID is 0.

    :param text: the geometry text
    :raises ValueError: if the text is not one of the forms the features use
    """
    srid = 0
    body = text.strip()
    if body.startswith('SRID='):
        prefix, separator, body = body.partition(';')
        if not separator:
            raise ValueError(f"EWKT prefix is not terminated by a semicolon: {text}")
        srid = int(prefix[len('SRID='):])
    reader = _WktReader(body, srid)
    geometry = reader.read_geometry()
    reader.expect_end()
    return geometry


class _WktReader(object):
    """A cursor over geometry text that reads one tagged geometry at a time."""

    def __init__(self, text, srid):
        self._text = text
        self._srid = srid
        self._position = 0

    def read_geometry(self):
        """Reads a geometry written as its type name, its optional Z tag and its body."""
        name = self._read_name()
        kind = _KINDS.get(name)
        if kind is None:
            raise ValueError(f"Unknown WKT type: {name}")
        return self._read_body(kind, self._read_z_tag())

    def expect_end(self):
        self._skip_whitespace()
        if self._position != len(self._text):
            raise ValueError(f"Unexpected trailing text in WKT input: {self._text[self._position:]}")

    def _read_body(self, kind, has_z):
        """
        Reads either EMPTY or the parenthesized body of one kind. It also reads a part of a
        collection and a ring of a polygon, which WKT writes as a body with no type name.
        """
        if self._read_empty():
            # the public empty() constructors always give a 2D value, so the z dimension of an
            # empty geometry is applied directly
            return kind._empty_with_z(self._srid, has_z)
        if kind is Point:
            return self._read_wrapped(lambda: self._read_coordinate(has_z))
        if kind is LineString:
            return LineString(self._read_wrapped(
                lambda: self._read_list(lambda: self._read_coordinate(has_z))))
        if kind is Polygon:
            rings = self._read_wrapped(lambda: self._read_list(
                lambda: self._read_body(LineString, has_z)))
            return Polygon(rings[0], rings[1:])
        if kind is GeometryCollection:
            return GeometryCollection(self._read_wrapped(
                lambda: self._read_list(self.read_geometry)))
        element = _ELEMENTS[kind]
        return kind(self._read_wrapped(
            lambda: self._read_list(lambda: self._read_body(element, has_z))))

    def _read_list(self, read_item):
        """Reads items separated by commas, up to but not including the closing parenthesis."""
        items = [read_item()]
        while self._read_symbol(','):
            items.append(read_item())
        return items

    def _read_coordinate(self, has_z):
        x = self._read_number()
        y = self._read_number()
        z = self._read_number() if has_z else None
        return Point(x, y, z, srid=self._srid)

    def _read_wrapped(self, read_body):
        self._expect_symbol('(')
        body = read_body()
        self._expect_symbol(')')
        return body

    def _read_name(self):
        self._skip_whitespace()
        match = _NAME.match(self._text, self._position)
        if match is None:
            raise ValueError(f"Expected a WKT type name at offset {self._position}: {self._text}")
        self._position = match.end()
        return match.group().upper()

    def _read_z_tag(self):
        return self._read_word('Z')

    def _read_empty(self):
        return self._read_word('EMPTY')

    def _read_number(self):
        self._skip_whitespace()
        match = _NUMBER.match(self._text, self._position)
        if match is None:
            raise ValueError(f"Expected a coordinate at offset {self._position}: {self._text}")
        self._position = match.end()
        return float(match.group())

    def _read_word(self, word):
        """
        Reads a keyword if it comes next, leaving the cursor where it was if it does not. The
        keyword is matched without regard to case and only on a word boundary, so a longer word
        that merely starts with it is not a match.
        """
        start = self._position
        self._skip_whitespace()
        end = self._position + len(word)
        if self._text[self._position:end].upper() == word and not self._text[end:end + 1].isalpha():
            self._position = end
            return True
        self._position = start
        return False

    def _read_symbol(self, symbol):
        self._skip_whitespace()
        if self._text.startswith(symbol, self._position):
            self._position += len(symbol)
            return True
        return False

    def _expect_symbol(self, symbol):
        if not self._read_symbol(symbol):
            raise ValueError(f"Expected '{symbol}' at offset {self._position}: {self._text}")

    def _skip_whitespace(self):
        while self._position < len(self._text) and self._text[self._position].isspace():
            self._position += 1
