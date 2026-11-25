/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/

package gremlingo

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"math"
	"strconv"
	"strings"

	gremlingo "github.com/apache/tinkerpop/gremlin-go/v4/driver"
)

// The geo[...] token of the feature files holds WKT, or EWKT when the SRID is not zero. The
// driver has no WKT reader, so this parser translates the text to WKB and the driver reads the
// value back from it. That keeps the parser to the shape of the text and leaves every rule about
// what a geometry may hold to the driver, including the empty 3D forms that no constructor
// builds.

const wkbBigEndian byte = 0x00

// wkbTypeCodes maps a WKT keyword to its ISO WKB type code, without the 1000 a z dimension adds.
var wkbTypeCodes = map[string]uint32{
	"POINT":              1,
	"LINESTRING":         2,
	"POLYGON":            3,
	"MULTIPOINT":         4,
	"MULTILINESTRING":    5,
	"MULTIPOLYGON":       6,
	"GEOMETRYCOLLECTION": 7,
}

// geometryFromWkt builds a geometry from WKT, or from EWKT when an "SRID=<srid>;" prefix is
// present.
func geometryFromWkt(text string) (gremlingo.Geometry, error) {
	srid := int32(0)
	wkt := strings.TrimSpace(text)
	if strings.HasPrefix(strings.ToUpper(wkt), "SRID=") {
		separator := strings.Index(wkt, ";")
		if separator < 0 {
			return nil, fmt.Errorf("an EWKT prefix needs a ';': %s", text)
		}
		parsed, err := strconv.ParseInt(wkt[len("SRID="):separator], 10, 32)
		if err != nil {
			return nil, fmt.Errorf("invalid SRID in %s: %w", text, err)
		}
		srid = int32(parsed)
		wkt = strings.TrimSpace(wkt[separator+1:])
	}

	reader := &wktReader{text: wkt}
	buf := &bytes.Buffer{}
	if err := reader.writeGeometry(buf); err != nil {
		return nil, err
	}
	reader.skipSpace()
	if !reader.atEnd() {
		return nil, fmt.Errorf("unexpected text after the geometry in %s", text)
	}
	return gremlingo.GeometryFromWKB(buf.Bytes(), srid)
}

// wktReader walks WKT text and writes the WKB of what it reads.
type wktReader struct {
	text string
	pos  int
}

// writeGeometry writes the geometry at the cursor, which names its own kind.
func (r *wktReader) writeGeometry(buf *bytes.Buffer) error {
	r.skipSpace()
	kind := strings.ToUpper(r.readWord())
	if _, known := wkbTypeCodes[kind]; !known {
		return fmt.Errorf("unknown geometry kind in WKT: %q", kind)
	}
	return r.writeTypedGeometry(buf, kind, r.acceptWord("Z"))
}

// writeTypedGeometry writes the body of a geometry whose kind and z dimension are already known,
// which is how a part of a multi-kind appears: the parent names the kind, so the part does not.
func (r *wktReader) writeTypedGeometry(buf *bytes.Buffer, kind string, hasZ bool) error {
	code := wkbTypeCodes[kind]
	if hasZ {
		code += 1000
	}
	buf.WriteByte(wkbBigEndian)
	writeWkbUint32(buf, code)

	if r.acceptWord("EMPTY") {
		if kind == "POINT" {
			// WKB has no empty point, so NaN coordinates stand for one
			writeWkbFloat64(buf, math.NaN())
			writeWkbFloat64(buf, math.NaN())
			if hasZ {
				writeWkbFloat64(buf, math.NaN())
			}
			return nil
		}
		writeWkbUint32(buf, 0)
		return nil
	}

	coordinate := func(b *bytes.Buffer) error { return r.writeCoordinate(b, hasZ) }
	ring := func(b *bytes.Buffer) error { return r.writeList(b, coordinate) }

	switch kind {
	case "POINT":
		if err := r.expect('('); err != nil {
			return err
		}
		if err := r.writeCoordinate(buf, hasZ); err != nil {
			return err
		}
		return r.expect(')')
	case "LINESTRING":
		return r.writeList(buf, coordinate)
	case "POLYGON":
		return r.writeList(buf, ring)
	case "MULTIPOINT":
		return r.writeList(buf, func(b *bytes.Buffer) error { return r.writeTypedGeometry(b, "POINT", hasZ) })
	case "MULTILINESTRING":
		return r.writeList(buf, func(b *bytes.Buffer) error { return r.writeTypedGeometry(b, "LINESTRING", hasZ) })
	case "MULTIPOLYGON":
		return r.writeList(buf, func(b *bytes.Buffer) error { return r.writeTypedGeometry(b, "POLYGON", hasZ) })
	case "GEOMETRYCOLLECTION":
		return r.writeList(buf, r.writeGeometry)
	default:
		return fmt.Errorf("unknown geometry kind in WKT: %q", kind)
	}
}

// writeList reads a comma separated list in parentheses and writes the item count followed by
// the items, which is how WKB holds every list.
func (r *wktReader) writeList(buf *bytes.Buffer, writeItem func(*bytes.Buffer) error) error {
	if err := r.expect('('); err != nil {
		return err
	}
	items := &bytes.Buffer{}
	count := uint32(0)
	for {
		if err := writeItem(items); err != nil {
			return err
		}
		count++
		r.skipSpace()
		if !r.accept(',') {
			break
		}
	}
	if err := r.expect(')'); err != nil {
		return err
	}
	writeWkbUint32(buf, count)
	buf.Write(items.Bytes())
	return nil
}

// writeCoordinate reads one coordinate and writes its ordinates.
func (r *wktReader) writeCoordinate(buf *bytes.Buffer, hasZ bool) error {
	ordinates := 2
	if hasZ {
		ordinates = 3
	}
	for i := 0; i < ordinates; i++ {
		value, err := r.readNumber()
		if err != nil {
			return err
		}
		writeWkbFloat64(buf, value)
	}
	return nil
}

func (r *wktReader) atEnd() bool {
	return r.pos >= len(r.text)
}

func (r *wktReader) skipSpace() {
	for !r.atEnd() && (r.text[r.pos] == ' ' || r.text[r.pos] == '\t') {
		r.pos++
	}
}

// accept consumes the next character when it is c, after any spaces.
func (r *wktReader) accept(c byte) bool {
	r.skipSpace()
	if r.atEnd() || r.text[r.pos] != c {
		return false
	}
	r.pos++
	return true
}

func (r *wktReader) expect(c byte) error {
	if !r.accept(c) {
		return fmt.Errorf("expected %q at position %d of %s", string(c), r.pos, r.text)
	}
	return nil
}

// readWord reads a run of letters, after any spaces.
func (r *wktReader) readWord() string {
	r.skipSpace()
	start := r.pos
	for !r.atEnd() && isWktLetter(r.text[r.pos]) {
		r.pos++
	}
	return r.text[start:r.pos]
}

// acceptWord reads the word at the cursor when it is the given one, after any spaces.
func (r *wktReader) acceptWord(word string) bool {
	mark := r.pos
	if strings.ToUpper(r.readWord()) == word {
		return true
	}
	r.pos = mark
	return false
}

// readNumber reads a double, after any spaces.
func (r *wktReader) readNumber() (float64, error) {
	r.skipSpace()
	start := r.pos
	for !r.atEnd() && isWktNumeric(r.text[r.pos]) {
		r.pos++
	}
	value, err := strconv.ParseFloat(r.text[start:r.pos], 64)
	if err != nil {
		return 0, fmt.Errorf("expected a number at position %d of %s: %w", start, r.text, err)
	}
	return value, nil
}

func isWktLetter(c byte) bool {
	return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
}

func isWktNumeric(c byte) bool {
	return (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.' || c == 'e' || c == 'E'
}

func writeWkbUint32(buf *bytes.Buffer, value uint32) {
	var bits [4]byte
	binary.BigEndian.PutUint32(bits[:], value)
	buf.Write(bits[:])
}

func writeWkbFloat64(buf *bytes.Buffer, value float64) {
	var bits [8]byte
	binary.BigEndian.PutUint64(bits[:], math.Float64bits(value))
	buf.Write(bits[:])
}
