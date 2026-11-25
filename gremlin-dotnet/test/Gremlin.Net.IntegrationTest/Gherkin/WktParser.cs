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

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using Gremlin.Net.Structure.Geo;

namespace Gremlin.Net.IntegrationTest.Gherkin
{
    /// <summary>
    ///     Reads the WKT and EWKT text that the feature files hold in their <c>geo[]</c> tokens. The text is
    ///     rewritten as ISO WKB and handed to <see cref="Geometry.FromWkb" />, so the geometry is built by the
    ///     same reader the driver uses.
    /// </summary>
    internal sealed class WktParser
    {
        private static readonly IDictionary<string, int> TypeCodes = new Dictionary<string, int>
        {
            { "POINT", 1 },
            { "LINESTRING", 2 },
            { "POLYGON", 3 },
            { "MULTIPOINT", 4 },
            { "MULTILINESTRING", 5 },
            { "MULTIPOLYGON", 6 },
            { "GEOMETRYCOLLECTION", 7 }
        };

        private readonly string _text;
        private int _position;

        private WktParser(string text)
        {
            _text = text;
        }

        /// <summary>
        ///     Parses one geometry from WKT, or from EWKT when the text carries an <c>SRID=n;</c> prefix.
        /// </summary>
        /// <param name="text">The WKT or EWKT text.</param>
        /// <returns>The geometry the text describes.</returns>
        /// <exception cref="FormatException">Thrown when the text is not WKT this parser understands.</exception>
        internal static Geometry Parse(string text)
        {
            var wkt = text.Trim();
            var srid = 0;

            if (wkt.StartsWith("SRID=", StringComparison.Ordinal))
            {
                var separator = wkt.IndexOf(';');
                if (separator < 0)
                {
                    throw new FormatException($"An SRID prefix must end with ';': {text}");
                }

                var sridText = wkt.Substring("SRID=".Length, separator - "SRID=".Length);
                if (!int.TryParse(sridText, NumberStyles.Integer, CultureInfo.InvariantCulture, out srid))
                {
                    throw new FormatException($"An SRID prefix must hold an integer: {text}");
                }

                wkt = wkt.Substring(separator + 1).Trim();
            }

            var parser = new WktParser(wkt);
            using var wkb = new MemoryStream();
            parser.WriteGeometry(wkb, null);
            parser.SkipWhitespace();
            if (parser._position != wkt.Length)
            {
                throw new FormatException($"Unexpected text after the geometry: {text}");
            }

            return Geometry.FromWkb(wkb.ToArray(), srid);
        }

        /// <summary>
        ///     Writes one geometry, taking its z dimension from its parent when it has one.
        /// </summary>
        private void WriteGeometry(Stream wkb, bool? parentHasZ)
        {
            var kind = ReadName();
            if (!TypeCodes.TryGetValue(kind, out var typeCode))
            {
                throw new FormatException($"Unknown geometry type: {kind}");
            }

            var hasZ = ReadZ() || parentHasZ == true;
            wkb.WriteByte(0x00);
            WriteInt(wkb, hasZ ? typeCode + 1000 : typeCode);

            if (ReadEmpty())
            {
                if (typeCode == 1)
                {
                    WriteCoordinates(wkb, hasZ, true);
                }
                else
                {
                    WriteInt(wkb, 0);
                }

                return;
            }

            Expect('(');
            switch (typeCode)
            {
                case 1:
                    WriteCoordinates(wkb, hasZ, false);
                    break;
                case 2:
                    WriteCoordinateList(wkb, hasZ);
                    break;
                case 3:
                    WriteRingList(wkb, hasZ);
                    break;
                case 4:
                    WriteParts(wkb, hasZ, (parts, partHasZ) =>
                    {
                        parts.WriteByte(0x00);
                        WriteInt(parts, partHasZ ? 1001 : 1);
                        if (ReadEmpty())
                        {
                            WriteCoordinates(parts, partHasZ, true);
                            return;
                        }

                        Expect('(');
                        WriteCoordinates(parts, partHasZ, false);
                        Expect(')');
                    });
                    break;
                case 5:
                    WriteParts(wkb, hasZ, (parts, partHasZ) =>
                    {
                        parts.WriteByte(0x00);
                        WriteInt(parts, partHasZ ? 1002 : 2);
                        Expect('(');
                        WriteCoordinateList(parts, partHasZ);
                        Expect(')');
                    });
                    break;
                case 6:
                    WriteParts(wkb, hasZ, (parts, partHasZ) =>
                    {
                        parts.WriteByte(0x00);
                        WriteInt(parts, partHasZ ? 1003 : 3);
                        Expect('(');
                        WriteRingList(parts, partHasZ);
                        Expect(')');
                    });
                    break;
                default:
                    WriteParts(wkb, hasZ, (parts, partHasZ) => WriteGeometry(parts, partHasZ));
                    break;
            }

            Expect(')');
        }

        /// <summary>
        ///     Writes a comma separated list of parts behind its count, buffering the parts so that the count is
        ///     known before it is written.
        /// </summary>
        private void WriteParts(Stream wkb, bool hasZ, Action<Stream, bool> writePart)
        {
            using var parts = new MemoryStream();
            var count = 0;
            do
            {
                writePart(parts, hasZ);
                count++;
            } while (ReadComma());

            WriteInt(wkb, count);
            wkb.Write(parts.ToArray(), 0, (int) parts.Length);
        }

        /// <summary>
        ///     Writes the rings of a polygon, each as a count and its coordinates.
        /// </summary>
        private void WriteRingList(Stream wkb, bool hasZ)
        {
            WriteParts(wkb, hasZ, (parts, partHasZ) =>
            {
                Expect('(');
                WriteCoordinateList(parts, partHasZ);
                Expect(')');
            });
        }

        /// <summary>
        ///     Writes a comma separated list of coordinates behind its count.
        /// </summary>
        private void WriteCoordinateList(Stream wkb, bool hasZ)
        {
            WriteParts(wkb, hasZ, (parts, partHasZ) => WriteCoordinates(parts, partHasZ, false));
        }

        private void WriteCoordinates(Stream wkb, bool hasZ, bool empty)
        {
            WriteDouble(wkb, empty ? double.NaN : ReadDouble());
            WriteDouble(wkb, empty ? double.NaN : ReadDouble());
            if (hasZ)
            {
                WriteDouble(wkb, empty ? double.NaN : ReadDouble());
            }
        }

        private string ReadName()
        {
            SkipWhitespace();
            var start = _position;
            while (_position < _text.Length && char.IsLetter(_text[_position]))
            {
                _position++;
            }

            if (start == _position)
            {
                throw new FormatException($"Expected a geometry type at position {start}: {_text}");
            }

            return _text.Substring(start, _position - start).ToUpperInvariant();
        }

        /// <summary>
        ///     Consumes a standalone <c>Z</c> dimension marker if one follows.
        /// </summary>
        private bool ReadZ() => ReadWord("Z");

        /// <summary>
        ///     Consumes an <c>EMPTY</c> marker if one follows.
        /// </summary>
        private bool ReadEmpty() => ReadWord("EMPTY");

        private bool ReadWord(string word)
        {
            SkipWhitespace();
            if (string.Compare(_text, _position, word, 0, word.Length, StringComparison.OrdinalIgnoreCase) != 0)
            {
                return false;
            }

            var after = _position + word.Length;
            if (after < _text.Length && char.IsLetter(_text[after]))
            {
                return false;
            }

            _position = after;
            return true;
        }

        private bool ReadComma()
        {
            SkipWhitespace();
            if (Peek() != ',')
            {
                return false;
            }

            _position++;
            return true;
        }

        private double ReadDouble()
        {
            SkipWhitespace();
            var start = _position;
            while (_position < _text.Length && IsNumberChar(_text[_position]))
            {
                _position++;
            }

            var token = _text.Substring(start, _position - start);
            if (!double.TryParse(token, NumberStyles.Float, CultureInfo.InvariantCulture, out var value))
            {
                throw new FormatException($"Expected a coordinate at position {start}: {_text}");
            }

            return value;
        }

        private static bool IsNumberChar(char c) =>
            char.IsDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E';

        private void Expect(char expected)
        {
            SkipWhitespace();
            if (Peek() != expected)
            {
                throw new FormatException($"Expected '{expected}' at position {_position}: {_text}");
            }

            _position++;
        }

        private char Peek()
        {
            SkipWhitespace();
            return _position < _text.Length ? _text[_position] : '\0';
        }

        private void SkipWhitespace()
        {
            while (_position < _text.Length && char.IsWhiteSpace(_text[_position]))
            {
                _position++;
            }
        }

        private static void WriteInt(Stream wkb, int value)
        {
            wkb.WriteByte((byte) (value >> 24));
            wkb.WriteByte((byte) (value >> 16));
            wkb.WriteByte((byte) (value >> 8));
            wkb.WriteByte((byte) value);
        }

        private static void WriteDouble(Stream wkb, double value)
        {
            var bits = BitConverter.DoubleToInt64Bits(value);
            for (var shift = 56; shift >= 0; shift -= 8)
            {
                wkb.WriteByte((byte) (bits >> shift));
            }
        }
    }
}
