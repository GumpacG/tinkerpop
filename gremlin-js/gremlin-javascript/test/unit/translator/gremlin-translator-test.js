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

import assert from 'assert';
import { GremlinTranslator } from '../../../lib/language/translator/GremlinTranslator.js';
import { TranslatorException } from '../../../lib/language/translator/TranslatorException.js';
import { Translator } from '../../../lib/language/translator/Translator.js';
import TranslateVisitor from '../../../lib/language/translator/TranslateVisitor.js';
import JavascriptTranslateVisitor from '../../../lib/language/translator/JavascriptTranslateVisitor.js';
import PythonTranslateVisitor from '../../../lib/language/translator/PythonTranslateVisitor.js';
import GoTranslateVisitor from '../../../lib/language/translator/GoTranslateVisitor.js';
import DotNetTranslateVisitor from '../../../lib/language/translator/DotNetTranslateVisitor.js';
import JavaTranslateVisitor from '../../../lib/language/translator/JavaTranslateVisitor.js';
import GroovyTranslateVisitor from '../../../lib/language/translator/GroovyTranslateVisitor.js';
import AnonymizedTranslateVisitor from '../../../lib/language/translator/AnonymizedTranslateVisitor.js';

// Geometry translations shared by the Java and Groovy tests, with the output of the Java translator.
const jvmGeometryTranslations = [
  // the string is decoded and trimmed
  ['g.inject(Geometry("POINT(1 2)"s))', 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
  ['g.inject(Geometry("POINT(1\\u00202)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
  ['g.inject(Geometry("POINT(1\\0402)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
  ['g.inject(Geometry("POINT(1 2)\\u000a"))', 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
  ["g.inject(Geometry('POINT(1 2)'s))", 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
  // a line break inside the text is escaped
  ['g.inject(Geometry("POINT(1\\u000a2)"))', 'g.inject(Geometry.fromWkt("POINT(1\\n2)"))'],
  // a GType name is written upper case
  ['g.inject(123).is(P.typeOf(GType.lineString))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
  ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
  // an SRID is read exactly, in each integer form the grammar accepts
  ['g.inject(Geometry("POINT(1 2)", 32618I))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  ['g.inject(Geometry("POINT(1 2)", 32618L))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  ['g.inject(Geometry("POINT(1 2)", 32618N))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  ['g.inject(Geometry("POINT(1 2)", 32_618))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  ['g.inject(Geometry("POINT(1 2)", 0x7F5A))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32602))'],
  ['g.inject(Geometry("POINT(1 2)", 017))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 15))'],
  ['g.inject(Geometry("POINT(1 2)", -0x7F5A))', 'g.inject(Geometry.fromWkt("POINT(1 2)", -32602))'],
  ['g.inject(Geometry("POINT(1 2)", 017N))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 17))'],
  // an EWKT prefix moves into the second argument, and is accepted where it agrees with an explicit one
  ['g.inject(Geometry("SRID=4326;POINT(1 2)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)", Model.GEOGRAPHIC))'],
  ['g.inject(Geometry("SRID=32618;POINT(1 2)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  // the largest prefix SRID, and the most digits a prefix may hold
  ['g.inject(Geometry("SRID=2147483647;POINT(1 2)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 2147483647))'],
  ['g.inject(Geometry("SRID=0000032618;POINT(1 2)"))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  [
    'g.inject(Geometry("SRID=4326;POINT(1 2)", GEOGRAPHIC))',
    'g.inject(Geometry.fromWkt("POINT(1 2)", Model.GEOGRAPHIC))',
  ],
  ['g.inject(Geometry("SRID=32618;POINT(1 2)", 32618))', 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
  // the qualified model form the feature files do not use
  ['g.inject(Geometry("POINT(1 2)", Model.GEOGRAPHIC))', 'g.inject(Geometry.fromWkt("POINT(1 2)", Model.GEOGRAPHIC))'],
  // a Model value on its own, which always carries its prefix
  ['g.inject(Model.OTHER)', 'g.inject(Model.OTHER)'],
  // the geo predicate forms the feature files do not use
  ['g.V().has("loc", geoWithin(x))', 'g.V().has("loc", GeoP.geoWithin(x))'],
  [
    'g.V().has("loc", geoWithin(Geometry("POINT(1 2)")).negate())',
    'g.V().has("loc", GeoP.geoWithin(Geometry.fromWkt("POINT(1 2)")).negate())',
  ],
  [
    'g.V().has("loc", P.not(geoWithin(Geometry("POINT(1 2)"))))',
    'g.V().has("loc", P.not(GeoP.geoWithin(Geometry.fromWkt("POINT(1 2)"))))',
  ],
  [
    'g.V().has("loc", geoWithin(Geometry("POINT(1 2)")).or(geoContains(Geometry("POINT(1 2)"))))',
    'g.V().has("loc", GeoP.geoWithin(Geometry.fromWkt("POINT(1 2)")).or(GeoP.geoContains(Geometry.fromWkt("POINT(1 2)"))))',
  ],
];

// Geometry literals the Java translator rejects.
const jvmGeometryErrors = [
  ['g.inject(Geometry("POINT(1 2)", 32618B))', 'Invalid Geometry literal: 32618B is out of range for its type'],
  ['g.inject(Geometry("POINT(1 2)", 2147483648L))', 'Invalid Geometry literal: SRID is out of range for a 32-bit integer'],
  ['g.inject(Geometry("POINT(1 2)", 2147483648))', 'Invalid Geometry literal: SRID is out of range for a 32-bit integer'],
  ['g.inject(Geometry("POINT(1 2)", 0x1N))', 'Invalid Geometry literal: 0x1N is not a decimal BigInteger'],
  [
    'g.inject(Geometry("SRID=4326;POINT(1 2)", CARTESIAN))',
    'Invalid Geometry literal: EWKT SRID 4326 conflicts with model CARTESIAN',
  ],
  [
    'g.inject(Geometry("SRID=32618;POINT(1 2)", GEOGRAPHIC))',
    'Invalid Geometry literal: EWKT SRID 32618 conflicts with model GEOGRAPHIC',
  ],
  ['g.inject(Geometry("SRID=4326;POINT(1 2)", 0))', 'Invalid Geometry literal: EWKT SRID 4326 conflicts with argument SRID 0'],
  [
    'g.inject(Geometry("SRID=2147483648;POINT(1 2)"))',
    'Invalid Geometry literal: SRID is out of range for a 32-bit integer',
  ],
  [
    'g.inject(Geometry("SRID=-2147483649;POINT(1 2)"))',
    'Invalid Geometry literal: SRID is out of range for a 32-bit integer',
  ],
  ['g.inject(Geometry("SRID=00000000000;POINT(1 2)"))', 'Invalid Geometry literal: SRID has more than 10 digits'],
  // a second prefix, at the position the JVM WKT reader reports, counting the whitespace it skips
  [
    'g.inject(Geometry("  SRID=4326;  SRID=4326;POINT(1 2)"))',
    'Invalid Geometry literal: Unknown geometry type at position 14',
  ],
];

describe('GremlinTranslator', function () {
  describe('basic translation', function () {
    const tests = [
      // basic traversals
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', 'g.V("name")'],
      ['g.V().hasLabel("person")', 'g.V().hasLabel("person")'],
      ['g.V().out("knows")', 'g.V().out("knows")'],
      // reserved word mapping
      ['g.V().in("knows")', 'g.V().in_("knows")'],
      ['g.V().from("a")', 'g.V().from_("a")'],
      // anonymous traversal
      ['g.V().where(__.out("knows"))', 'g.V().where(__.out("knows"))'],
      // enum explicit naming
      ['g.V().order().by(T.id)', 'g.V().order().by(T.id)'],
      ['g.V().order().by(Order.asc)', 'g.V().order().by(Order.asc)'],
      // predicates
      ['g.V().has("age", P.gt(30))', 'g.V().has("age", P.gt(30))'],
      ['g.V().has("name", TextP.containing("mar"))', 'g.V().has("name", TextP.containing("mar"))'],
      // integer suffix stripping
      ['g.V().range(0, 10l)', 'g.V().range(0, 10)'],
      // float suffix stripping
      ['g.V().has("weight", P.gt(0.5f))', 'g.V().has("weight", P.gt(0.5))'],
      // Map literal
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.addV("person").property(T.id, new Map([[T.label, "person"]]))'],
      // Set literal
      ['g.inject({1,2,3})', 'g.inject(new Set([1, 2, 3]))'],
      // Collection literal
      ['g.inject([1,2,3])', 'g.inject([1, 2, 3])'],
      // Nan/Inf
      ['g.inject(NaN)', 'g.inject(Number.NaN)'],
      ['g.inject(Infinity)', 'g.inject(Number.POSITIVE_INFINITY)'],
      ['g.inject(-Infinity)', 'g.inject(Number.NEGATIVE_INFINITY)'],
      // Binary literal
      ['g.inject(Binary("AQID"))', 'g.inject(Buffer.from("AQID",\'base64\'))'],
      // terminal steps — pass through as-is in JavaScript
      ['g.V().toList()', 'g.V().toList()'],
      ['g.V().toSet()', 'g.V().toSet()'],
      ['g.V().toBulkSet()', 'g.V().toBulkSet()'],
      ['g.V().iterate()', 'g.V().iterate()'],
      ['g.V().hasNext()', 'g.V().hasNext()'],
      ['g.V().tryNext()', 'g.V().tryNext()'],
      ['g.V().next()', 'g.V().next()'],
      ['g.V().next(2)', 'g.V().next(2)'],
      // transaction steps — pass through as-is in JavaScript
      ['g.tx().begin()', 'g.tx().begin()'],
      ['g.tx().commit()', 'g.tx().commit()'],
      ['g.tx().rollback()', 'g.tx().rollback()'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.inject(123).is(P.typeOf(GType.lineString))'],
      ['g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))', 'g.inject(123).is(P.typeOf(GType.geometryCollection))'],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(123).is(P.typeOf(GType.lineString))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        const result = GremlinTranslator.translate(input);
        assert.strictEqual(result.getTranslated(), expected);
      });
    });
  });

  describe('custom graphTraversalSourceName', function () {
    it('uses custom source name', function () {
      const result = GremlinTranslator.translate('g.V()', 'source');
      assert.strictEqual(result.getTranslated(), 'source.V()');
    });
  });

  describe('Translator registry', function () {
    it('accepts JAVASCRIPT key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'JAVASCRIPT');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has JAVASCRIPT factory', function () {
      const visitor = Translator.JAVASCRIPT('g');
      assert.ok(visitor instanceof JavascriptTranslateVisitor);
    });
  });

  describe('custom visitor', function () {
    it('accepts TranslateVisitor subclass directly', function () {
      const visitor = new JavascriptTranslateVisitor('myG');
      const result = GremlinTranslator.translate('g.V()', 'myG', visitor);
      assert.strictEqual(result.getTranslated(), 'myG.V()');
    });
  });

  describe('parameter tracking', function () {
    it('tracks variables as parameters', function () {
      const result = GremlinTranslator.translate('g.V(vid1)');
      assert.ok(result.getParameters().has('vid1'));
    });

    it('returns original query', function () {
      const query = 'g.V().hasLabel("person")';
      const result = GremlinTranslator.translate(query);
      assert.strictEqual(result.getOriginal(), query);
    });
  });

  describe('range literals throw', function () {
    it('throws TranslatorException for range literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(1..5)'),
        TranslatorException,
      );
    });

    it('throws TranslatorException for character literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject("a"c)'),
        TranslatorException,
      );
    });

    it('throws TranslatorException for duration literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(Duration(9000,0))'),
        TranslatorException,
      );
    });
  });
});

describe('PythonTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'PYTHON').getTranslated();

  describe('basic translation', function () {
    const tests = [
      // basic traversals — camelCase methods become snake_case
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', "g.V('name')"],
      ['g.V().hasLabel("person")', "g.V().has_label('person')"],
      ['g.V().out("knows")', "g.V().out('knows')"],
      // reserved word mapping
      ['g.V().in("knows")', "g.V().in_('knows')"],
      ['g.V().from("a")', "g.V().from_('a')"],
      ['g.V().filter(__.has("name"))', 'g.V().filter_(__.has(\'name\'))'],
      // string literals use single quotes
      ['g.V().has("name", "marko")', "g.V().has('name', 'marko')"],
      // boolean capitalisation
      ['g.V().has("active", true)', "g.V().has('active', True)"],
      ['g.V().has("active", false)', "g.V().has('active', False)"],
      // null → None
      ['g.V().has("name", null)', "g.V().has('name', None)"],
      // enum explicit naming — T.id → T.id_ in Python (id is a builtin)
      ['g.V().order().by(T.id)', 'g.V().order().by(T.id_)'],
      ['g.V().order().by(Order.asc)', 'g.V().order().by(Order.asc)'],
      // camelCase step names → snake_case
      ['g.V().valueMap()', 'g.V().value_map()'],
      ['g.V().outE()', 'g.V().out_e()'],
      // predicates
      ['g.V().has("age", P.gt(30))', "g.V().has('age', P.gt(30))"],
      ['g.V().has("name", TextP.containing("mar"))', "g.V().has('name', TextP.containing('mar'))"],
      // integer suffix stripping
      ['g.V().range(0, 10l)', 'g.V().range_(0, long(10))'],
      // float suffix stripping
      ['g.V().has("weight", P.gt(0.5f))', "g.V().has('weight', P.gt(0.5))"],
      // NaN/Inf
      ["g.inject(NaN)", "g.inject(float('nan'))"],
      ["g.inject(Infinity)", "g.inject(float('inf'))"],
      ["g.inject(-Infinity)", "g.inject(float('-inf'))"],
      // Collection literals
      ['g.inject([1,2,3])', 'g.inject([1, 2, 3])'],
      // Set literal (non-empty)
      ['g.inject({1,2,3})', 'g.inject({1, 2, 3})'],
      // Character literal
      ['g.inject("a"c)', "g.inject(SingleChar('a'))"],
      ['g.inject("\\""c)', "g.inject(SingleChar('\\\"'))"],
      ['g.inject("\\\\"c)', "g.inject(SingleChar('\\\\'))"],
      ["g.inject(\"'\"c)", "g.inject(SingleChar('\\''))"],
      ["g.inject('\\''c)", "g.inject(SingleChar('\\''))"],
      ['g.inject("\\7"c)', "g.inject(SingleChar('\\7'))"],
      ['g.inject("\\07"c)', "g.inject(SingleChar('\\07'))"],
      // Duration literal
      ['g.inject(Duration(9000,0))', 'g.inject(timedelta(seconds=9000))'],
      // Binary literal
      ['g.inject(Binary("AQID"))', "g.inject(base64.b64decode('AQID'))"],
      // Map literal — T.id → T.id_ in Python
      ['g.addV("person").property(T.id, [(T.label): "person"])', "g.add_v('person').property(T.id_, { T.label: 'person' })"],
      // terminal steps — camelCase → snake_case
      ['g.V().toList()', 'g.V().to_list()'],
      ['g.V().toSet()', 'g.V().to_set()'],
      ['g.V().toBulkSet()', 'g.V().to_bulk_set()'],
      ['g.V().iterate()', 'g.V().iterate()'],
      ['g.V().hasNext()', 'g.V().has_next()'],
      ['g.V().tryNext()', 'g.V().try_next()'],
      ['g.V().next()', 'g.V().next()'],
      ['g.V().next(2)', 'g.V().next(2)'],
      // transaction steps — no change in Python
      ['g.tx().begin()', 'g.tx().begin()'],
      ['g.tx().commit()', 'g.tx().commit()'],
      ['g.tx().rollback()', 'g.tx().rollback()'],
      // a GType name is written upper case
      ['g.inject(1).is(P.typeOf(GType.bigDecimal))', 'g.inject(1).is_(P.type_of(GType.BIGDECIMAL))'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.inject(123).is_(P.type_of(GType.LINESTRING))'],
      ['g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))', 'g.inject(123).is_(P.type_of(GType.GEOMETRYCOLLECTION))'],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(123).is_(P.type_of(GType.LINESTRING))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('Translator registry', function () {
    it('accepts PYTHON key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'PYTHON');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has PYTHON factory', function () {
      const visitor = Translator.PYTHON('g');
      assert.ok(visitor instanceof PythonTranslateVisitor);
    });
  });

  describe('range literals throw', function () {
    it('throws TranslatorException for range literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(1..5)', 'g', 'PYTHON'),
        TranslatorException,
      );
    });
  });
});

describe('GoTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'GO').getTranslated();

  describe('basic translation', function () {
    const tests = [
      // basic traversals — all step methods become PascalCase
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', 'g.V("name")'],
      ['g.V().hasLabel("person")', 'g.V().HasLabel("person")'],
      ['g.V().out("knows")', 'g.V().Out("knows")'],
      // anonymous traversal uses gremlingo.T__.
      ['g.V().where(__.out("knows"))', 'g.V().Where(gremlingo.T__.Out("knows"))'],
      // enum explicit naming with gremlingo. prefix
      ['g.V().order().by(T.id)', 'g.V().Order().By(gremlingo.T.Id)'],
      ['g.V().order().by(Order.asc)', 'g.V().Order().By(gremlingo.Order.Asc)'],
      // predicates get gremlingo. prefix and PascalCase method
      ['g.V().has("age", P.gt(30))', 'g.V().Has("age", gremlingo.P.Gt(30))'],
      ['g.V().has("name", TextP.containing("mar"))', 'g.V().Has("name", gremlingo.TextP.Containing("mar"))'],
      // integer suffixes become Go type casts
      ['g.V().range(0, 10l)', 'g.V().Range(0, int64(10))'],
      // float suffix
      ['g.V().has("weight", P.gt(0.5f))', 'g.V().Has("weight", gremlingo.P.Gt(float32(0.5)))'],
      // Map literal
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.AddV("person").Property(gremlingo.T.Id, map[interface{}]interface{}{gremlingo.T.Label: "person" })'],
      // Set literal
      ['g.inject({1,2,3})', 'g.Inject(gremlingo.NewSimpleSet(1, 2, 3))'],
      // Collection literal
      ['g.inject([1,2,3])', 'g.Inject([]interface{}{1, 2, 3})'],
      // NaN/Inf
      ['g.inject(NaN)', 'g.Inject(math.NaN())'],
      ['g.inject(Infinity)', 'g.Inject(math.Inf(1))'],
      ['g.inject(-Infinity)', 'g.Inject(math.Inf(-1))'],
      // Duration literal
      ['g.inject(Duration(9000,0))', 'g.Inject(time.Duration(9000000000000))'],
      // Binary literal
      ['g.inject(Binary("AQID"))', 'g.Inject(gremlingo.ByteBuffer{Data: []byte{1,2,3}})'],
      // Character literals
      ['g.inject("a"c)', "g.Inject(gremlingo.Char('a'))"],
      ['g.inject("\\""c)', "g.Inject(gremlingo.Char('\"'))"],
      ['g.inject("\\\\"c)', "g.Inject(gremlingo.Char('\\\\'))"],
      ["g.inject(\"'\"c)", "g.Inject(gremlingo.Char('\\''))"],
      ["g.inject('\\''c)", "g.Inject(gremlingo.Char('\\''))"],
      ['g.inject("\\7"c)', 'g.Inject(gremlingo.Char(0o7))'],
      ['g.inject("\\07"c)', 'g.Inject(gremlingo.Char(0o7))'],
      // null → nil
      ['g.V().has("name", null)', 'g.V().Has("name", nil)'],
      // terminal steps — PascalCase
      ['g.V().toList()', 'g.V().ToList()'],
      ['g.V().toSet()', 'g.V().ToSet()'],
      ['g.V().toBulkSet()', 'g.V().ToBulkSet()'],
      ['g.V().iterate()', 'g.V().Iterate()'],
      ['g.V().hasNext()', 'g.V().HasNext()'],
      ['g.V().tryNext()', 'g.V().TryNext()'],
      ['g.V().next()', 'g.V().Next()'],
      // next(n) → NextN(n) since Go does not support method overloading
      ['g.V().next(2)', 'g.V().NextN(2)'],
      // transaction steps — PascalCase
      ['g.tx().begin()', 'g.Tx().Begin()'],
      ['g.tx().commit()', 'g.Tx().Commit()'],
      ['g.tx().rollback()', 'g.Tx().Rollback()'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.Inject(123).Is(gremlingo.P.TypeOf(gremlingo.GType.LineString))'],
      [
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
        'g.Inject(123).Is(gremlingo.P.TypeOf(gremlingo.GType.GeometryCollection))',
      ],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.Inject(123).Is(gremlingo.P.TypeOf(gremlingo.GType.LineString))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('Translator registry', function () {
    it('accepts GO key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'GO');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has GO factory', function () {
      const visitor = Translator.GO('g');
      assert.ok(visitor instanceof GoTranslateVisitor);
    });
  });

  describe('range literals throw', function () {
    it('throws TranslatorException for range literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(1..5)', 'g', 'GO'),
        TranslatorException,
      );
    });
  });
});

describe('DotNetTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'DOTNET').getTranslated();

  describe('basic translation', function () {
    const tests = [
      // basic traversals — all step methods become PascalCase
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', 'g.V("name")'],
      ['g.V().hasLabel("person")', 'g.V().HasLabel("person")'],
      ['g.V().out("knows")', 'g.V().Out("knows")'],
      // anonymous traversal uses __. (no prefix)
      ['g.V().where(__.out("knows"))', 'g.V().Where(__.Out("knows"))'],
      // enum naming: prefix as-is, value capitalized
      ['g.V().order().by(T.id)', 'g.V().Order().By(T.Id)'],
      ['g.V().order().by(Order.asc)', 'g.V().Order().By(Order.Asc)'],
      // predicates: PascalCase method name
      ['g.V().has("age", P.gt(30))', 'g.V().Has("age", P.Gt(30))'],
      ['g.V().has("name", TextP.containing("mar"))', 'g.V().Has("name", TextP.Containing("mar"))'],
      // integer suffixes
      ['g.V().range(0, 10l)', 'g.V().Range<object>(0, 10l)'],
      ['g.V().range(0, 5i)', 'g.V().Range<object>(0, 5)'],
      // float suffixes — f/d kept for C#
      ['g.V().has("weight", P.gt(0.5f))', 'g.V().Has("weight", P.Gt(0.5f))'],
      // NaN / Inf
      ['g.inject(NaN)', 'g.Inject<object>(Double.NaN)'],
      ['g.inject(Infinity)', 'g.Inject<object>(Double.PositiveInfinity)'],
      ['g.inject(-Infinity)', 'g.Inject<object>(Double.NegativeInfinity)'],
      // Collections
      ['g.inject([1,2,3])', 'g.Inject<object>(new List<object> { 1, 2, 3 })'],
      ['g.inject({1,2,3})', 'g.Inject<object>(new HashSet<object> { 1, 2, 3 })'],
      // Character literal
      ['g.inject("a"c)', "g.Inject<object>('a')"],
      ['g.inject("\\""c)', "g.Inject<object>('\\\"')"],
      ['g.inject("\\\\"c)', "g.Inject<object>('\\\\')"],
      ["g.inject(\"'\"c)", "g.Inject<object>('\\'')"],
      ["g.inject('\\''c)", "g.Inject<object>('\\'')"],
      ['g.inject("\\7"c)', "g.Inject<object>('\\u0007')"],
      ['g.inject("\\07"c)', "g.Inject<object>('\\u0007')"],
      // Duration literal
      ['g.inject(Duration(9000,0))', 'g.Inject<object>(TimeSpan.FromTicks(90000000000L))'],
      // Binary literal
      ['g.inject(Binary("AQID"))', 'g.Inject<object>(Convert.FromBase64String("AQID"))'],
      // Map literal
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.AddV((string) "person").Property(T.Id, new Dictionary<object, object> {{ T.Label, "person" }})'],
      // values gets <object>
      ['g.V().values("name")', 'g.V().Values<object>("name")'],
      // null cast
      ['g.V().has("name", null)', 'g.V().Has("name", (object) null)'],
      // terminal steps — PascalCase
      ['g.V().toList()', 'g.V().ToList()'],
      ['g.V().toSet()', 'g.V().ToSet()'],
      ['g.V().toBulkSet()', 'g.V().ToBulkSet()'],
      ['g.V().iterate()', 'g.V().Iterate()'],
      ['g.V().hasNext()', 'g.V().HasNext()'],
      ['g.V().tryNext()', 'g.V().TryNext()'],
      ['g.V().next()', 'g.V().Next()'],
      ['g.V().next(2)', 'g.V().Next(2)'],
      // transaction steps — PascalCase
      ['g.tx().begin()', 'g.Tx().Begin()'],
      ['g.tx().commit()', 'g.Tx().Commit()'],
      ['g.tx().rollback()', 'g.Tx().Rollback()'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.Inject<object>(123).Is(P.TypeOf(GType.LineString))'],
      [
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
        'g.Inject<object>(123).Is(P.TypeOf(GType.GeometryCollection))',
      ],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.Inject<object>(123).Is(P.TypeOf(GType.LineString))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('Translator registry', function () {
    it('accepts DOTNET key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'DOTNET');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has DOTNET factory', function () {
      const visitor = Translator.DOTNET('g');
      assert.ok(visitor instanceof DotNetTranslateVisitor);
    });
  });

  describe('range literals throw', function () {
    it('throws TranslatorException for range literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(1..5)', 'g', 'DOTNET'),
        TranslatorException,
      );
    });
  });
});

describe('JavaTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'JAVA').getTranslated();

  describe('basic translation', function () {
    const tests = [
      // basic traversals — camelCase preserved
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', 'g.V("name")'],
      ['g.V().hasLabel("person")', 'g.V().hasLabel("person")'],
      ['g.V().out("knows")', 'g.V().out("knows")'],
      // anonymous traversal
      ['g.V().where(__.out("knows"))', 'g.V().where(__.out("knows"))'],
      // enum explicit naming — base class handles, as-is
      ['g.V().order().by(T.id)', 'g.V().order().by(T.id)'],
      ['g.V().order().by(Order.asc)', 'g.V().order().by(Order.asc)'],
      // predicates
      ['g.V().has("age", P.gt(30))', 'g.V().has("age", P.gt(30))'],
      ['g.V().has("name", TextP.containing("mar"))', 'g.V().has("name", TextP.containing("mar"))'],
      // integer suffixes
      ['g.V().range(0, 10l)', 'g.V().range(0, 10l)'],
      ['g.inject(1b)', 'g.inject(new Byte(1))'],
      ['g.inject(1s)', 'g.inject(new Short(1))'],
      ['g.inject(1i)', 'g.inject(1)'],
      ['g.inject(1n)', 'g.inject(new BigInteger("1"))'],
      // float suffixes
      ['g.V().has("weight", P.gt(0.5f))', 'g.V().has("weight", P.gt(0.5f))'],
      // NaN/Inf
      ['g.inject(NaN)', 'g.inject(Double.NaN)'],
      ['g.inject(Infinity)', 'g.inject(Double.POSITIVE_INFINITY)'],
      ['g.inject(-Infinity)', 'g.inject(Double.NEGATIVE_INFINITY)'],
      // Collection literals
      ['g.inject([1,2,3])', 'g.inject(new ArrayList<Object>() {{ add(1); add(2); add(3); }})'],
      // Set literal
      ['g.inject({1,2,3})', 'g.inject(new HashSet<Object>() {{ add(1); add(2); add(3); }})'],
      // Character literal
      ['g.inject("a"c)', "g.inject('a')"],
      ['g.inject("\\""c)', "g.inject('\\\"')"],
      ['g.inject("\\\\"c)', "g.inject('\\\\')"],
      ["g.inject(\"'\"c)", "g.inject('\\'')"],
      ["g.inject('\\''c)", "g.inject('\\'')"],
      ['g.inject("\\7"c)', "g.inject('\\7')"],
      ['g.inject("\\07"c)', "g.inject('\\07')"],
      // Duration literal
      ['g.inject(Duration(9000,0))', 'g.inject(Duration.ofSeconds(9000, 0))'],
      // Binary literal
      ['g.inject(Binary("AQID"))', 'g.inject(ByteBuffer.wrap(Base64.getDecoder().decode("AQID")))'],
      // Map literal
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.addV("person").property(T.id, new LinkedHashMap<Object, Object>() {{ put(T.label, "person"); }})'],
      // terminal steps — pass through as-is in Java
      ['g.V().toList()', 'g.V().toList()'],
      ['g.V().iterate()', 'g.V().iterate()'],
      ['g.V().next()', 'g.V().next()'],
      ['g.V().next(2)', 'g.V().next(2)'],
      // transaction steps — pass through as-is in Java
      ['g.tx().begin()', 'g.tx().begin()'],
      ['g.tx().commit()', 'g.tx().commit()'],
      ['g.tx().rollback()', 'g.tx().rollback()'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
      [
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
      ],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('geometry translation', function () {
    jvmGeometryTranslations.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('invalid geometry literals throw', function () {
    jvmGeometryErrors.forEach(function ([input, message]) {
      it(`throws for: ${input}`, function () {
        assert.throws(() => translate(input), { name: 'TranslatorException', message });
      });
    });
  });

  describe('Translator registry', function () {
    it('accepts JAVA key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'JAVA');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has JAVA factory', function () {
      const visitor = Translator.JAVA('g');
      assert.ok(visitor instanceof JavaTranslateVisitor);
    });
  });

  describe('range literals throw', function () {
    it('throws TranslatorException for range literal', function () {
      assert.throws(
        () => GremlinTranslator.translate('g.inject(1..5)', 'g', 'JAVA'),
        TranslatorException,
      );
    });
  });
});

describe('GroovyTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'GROOVY').getTranslated();

  describe('basic translation', function () {
    const tests = [
      // basic traversals — camelCase preserved
      ['g.V()', 'g.V()'],
      ['g.V(1)', 'g.V(1)'],
      ['g.V("name")', 'g.V("name")'],
      ['g.V().hasLabel("person")', 'g.V().hasLabel("person")'],
      ['g.V().out("knows")', 'g.V().out("knows")'],
      // anonymous traversal
      ['g.V().where(__.out("knows"))', 'g.V().where(__.out("knows"))'],
      // enum explicit naming — base class handles, as-is
      ['g.V().order().by(T.id)', 'g.V().order().by(T.id)'],
      ['g.V().order().by(Order.asc)', 'g.V().order().by(Order.asc)'],
      // predicates
      ['g.V().has("age", P.gt(30))', 'g.V().has("age", P.gt(30))'],
      ['g.V().has("name", TextP.containing("mar"))', 'g.V().has("name", TextP.containing("mar"))'],
      // integer suffixes
      ['g.V().range(0, 10l)', 'g.V().range(0, 10l)'],
      ['g.inject(1b)', 'g.inject((byte)1)'],
      ['g.inject(1s)', 'g.inject((short)1)'],
      ['g.inject(1i)', 'g.inject(1i)'],
      ['g.inject(1n)', 'g.inject(1g)'],
      // float suffixes
      ['g.V().has("weight", P.gt(0.5f))', 'g.V().has("weight", P.gt(0.5f))'],
      // NaN — base class outputs NaN as-is
      ['g.inject(NaN)', 'g.inject(NaN)'],
      // Infinity
      ['g.inject(Infinity)', 'g.inject(Double.POSITIVE_INFINITY)'],
      ['g.inject(-Infinity)', 'g.inject(Double.NEGATIVE_INFINITY)'],
      // Collection literals — base class handles as [items]
      ['g.inject([1,2,3])', 'g.inject([1, 2, 3])'],
      // Set literal — Groovy: [items] as Set
      ['g.inject({1,2,3})', 'g.inject([1, 2, 3] as Set)'],
      // Character literal
      ['g.inject("a"c)', "g.inject('a' as char)"],
      ['g.inject("\\""c)', "g.inject('\\\"' as char)"],
      ['g.inject("\\\\"c)', "g.inject('\\\\' as char)"],
      ["g.inject(\"'\"c)", "g.inject('\\'' as char)"],
      ["g.inject('\\''c)", "g.inject('\\'' as char)"],
      ['g.inject("\\7"c)', "g.inject('\\7' as char)"],
      ['g.inject("\\07"c)', "g.inject('\\07' as char)"],
      // Duration literal
      ['g.inject(Duration(9000,0))', 'g.inject(Duration.ofSeconds(9000, 0))'],
      // Binary literal
      ['g.inject(Binary("AQID"))', 'g.inject(ByteBuffer.wrap(Base64.getDecoder().decode("AQID")))'],
      // Map literal — Groovy preserves parens on expression keys
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.addV("person").property(T.id, [(T.label):"person"])'],
      // null as Map in mergeV
      ['g.mergeV(null)', 'g.mergeV((Map) null)'],
      // strategy no-args: output name as-is
      ['g.withStrategies(AdjacentToIncidentStrategy).V()', 'g.withStrategies(AdjacentToIncidentStrategy).V()'],
      // strategy with args: prepend new
      ['g.withStrategies(EdgeLabelVerificationStrategy(throwException: true, logWarning: false)).V().out()', 'g.withStrategies(new EdgeLabelVerificationStrategy(throwException:true, logWarning:false)).V().out()'],
      // withoutStrategies: no .class suffix
      ['g.withoutStrategies(EarlyLimitStrategy).V()', 'g.withoutStrategies(EarlyLimitStrategy).V()'],
      // terminal steps — pass through as-is in Groovy
      ['g.V().toList()', 'g.V().toList()'],
      ['g.V().iterate()', 'g.V().iterate()'],
      ['g.V().next()', 'g.V().next()'],
      ['g.V().next(2)', 'g.V().next(2)'],
      // transaction steps — pass through as-is in Groovy
      ['g.tx().begin()', 'g.tx().begin()'],
      ['g.tx().commit()', 'g.tx().commit()'],
      ['g.tx().rollback()', 'g.tx().rollback()'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
      [
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
      ],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(123).is(P.typeOf(GType.LINESTRING))'],
    ];

    tests.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('geometry translation', function () {
    jvmGeometryTranslations.forEach(function ([input, expected]) {
      it(`translates: ${input}`, function () {
        assert.strictEqual(translate(input), expected);
      });
    });
  });

  describe('invalid geometry literals throw', function () {
    jvmGeometryErrors.forEach(function ([input, message]) {
      it(`throws for: ${input}`, function () {
        assert.throws(() => translate(input), { name: 'TranslatorException', message });
      });
    });
  });

  describe('Translator registry', function () {
    it('accepts GROOVY key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'GROOVY');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has GROOVY factory', function () {
      const visitor = Translator.GROOVY('g');
      assert.ok(visitor instanceof GroovyTranslateVisitor);
    });
  });
});

describe('AnonymizedTranslateVisitor', function () {
  const translate = (q) => GremlinTranslator.translate(q, 'g', 'ANONYMIZED').getTranslated();

  describe('basic anonymization', function () {
    const tests = [
      // structure preserved, strings replaced
      ['g.V().hasLabel("person")', 'g.V().hasLabel(string0)'],
      // same value reuses same placeholder
      ['g.V().has("name", "name")', 'g.V().has(string0, string0)'],
      // different string values get different placeholders
      ['g.V().has("age", "marko")', 'g.V().has(string0, string1)'],
      // plain numbers — no suffix → number
      ['g.V().has("age", P.gt(30))', 'g.V().has(string0, P.gt(number0))'],
      ['g.V().has("age", P.between(26, 30))', 'g.V().has(string0, P.between(number0, number1))'],
      // integer suffix i → integer
      ['g.inject(1i)', 'g.inject(integer0)'],
      // integer suffix l → long
      ['g.inject(10l)', 'g.inject(long0)'],
      // integer suffix b → byte
      ['g.inject(1b)', 'g.inject(byte0)'],
      // integer suffix s → short
      ['g.inject(1s)', 'g.inject(short0)'],
      // integer suffix n → biginteger
      ['g.inject(1n)', 'g.inject(biginteger0)'],
      // float suffix f → float
      ['g.inject(0.5f)', 'g.inject(float0)'],
      // float suffix d → double
      ['g.inject(0.5d)', 'g.inject(double0)'],
      // float suffix m → bigdecimal
      ['g.inject(1.5m)', 'g.inject(bigdecimal0)'],
      // NaN → number
      ['g.inject(NaN)', 'g.inject(number0)'],
      // Infinity → number
      ['g.inject(Infinity)', 'g.inject(number0)'],
      ['-Infinity' + '' , 'number0'],  // handled via inject below
      // boolean
      ['g.inject(true)', 'g.inject(boolean0)'],
      ['g.inject(false)', 'g.inject(boolean0)'],
      // null → object
      ['g.inject(null)', 'g.inject(object0)'],
      // collection → list
      ['g.inject([1,2,3])', 'g.inject(list0)'],
      // set → set
      ['g.inject({1,2,3})', 'g.inject(set0)'],
      // character → character
      ['g.inject("a"c)', 'g.inject(character0)'],
      // duration -> duration
      ['g.inject(Duration(9000,0))', 'g.inject(duration0)'],
      // binary → bytebuffer
      ['g.inject(Binary("AQID"))', 'g.inject(bytebuffer0)'],
      // geometry -> the kind its WKT names, with a counter per kind
      ['g.inject(Geometry("POINT(1 2)"))', 'g.inject(point0)'],
      ['g.inject(Geometry("SRID=4326;POINT(1 2)"))', 'g.inject(point0)'],
      // the kind comes from the type keyword whether or not the rest is valid, as in GremlinTranslatorTest
      ['g.inject(Geometry("POINT nonsense"))', 'g.inject(point0)'],
      ['g.inject(Geometry("SRID=4326;POINT nonsense"))', 'g.inject(point0)'],
      ['g.inject(Geometry("NOT WKT"))', 'g.inject(geometry0)'],
      ['g.inject(Geometry(" linestring EMPTY", GEOGRAPHIC))', 'g.inject(linestring0)'],
      ['g.inject(Geometry("\\u0050OLYGON EMPTY", 32618))', 'g.inject(polygon0)'],
      ['g.inject(Geometry("POINT(1 2)"), Geometry("MULTIPOINT((1 2))"), Geometry("POINT(3 4)"), Geometry("POINT(1 2)"))',
        'g.inject(point0, multipoint0, point1, point0)'],
      ['g.inject(Geometry("MULTILINESTRING EMPTY"), Geometry("MULTIPOLYGON EMPTY"), Geometry("GEOMETRYCOLLECTION EMPTY"))',
        'g.inject(multilinestring0, multipolygon0, geometrycollection0)'],
      // map → map
      ['g.addV("person").property(T.id, [(T.label): "person"])', 'g.addV(string0).property(T.id, map0)'],
      // enums/traversal steps pass through unchanged
      ['g.V().order().by(T.id)', 'g.V().order().by(T.id)'],
      ['g.V().order().by(Order.asc)', 'g.V().order().by(Order.asc)'],
      // variables pass through unchanged
      ['g.V(vid1)', 'g.V(vid1)'],
      // GType names
      ['g.inject(123).is(P.typeOf(GType.LINESTRING))', 'g.inject(number0).is(P.typeOf(GType.LINESTRING))'],
      [
        'g.inject(123).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
        'g.inject(number0).is(P.typeOf(GType.GEOMETRYCOLLECTION))',
      ],
      ['g.inject(123).is(P.typeOf(lineString))', 'g.inject(number0).is(P.typeOf(GType.LINESTRING))'],
    ];

    tests
      .filter(([input]) => !input.startsWith('-Infinity'))
      .forEach(function ([input, expected]) {
        it(`anonymizes: ${input}`, function () {
          assert.strictEqual(translate(input), expected);
        });
      });

    it('anonymizes: g.inject(-Infinity)', function () {
      assert.strictEqual(translate('g.inject(-Infinity)'), 'g.inject(number0)');
    });
  });

  describe('Translator registry', function () {
    it('accepts ANONYMIZED key', function () {
      const result = GremlinTranslator.translate('g.V()', 'g', 'ANONYMIZED');
      assert.strictEqual(result.getTranslated(), 'g.V()');
    });

    it('has ANONYMIZED factory', function () {
      const visitor = Translator.ANONYMIZED('g');
      assert.ok(visitor instanceof AnonymizedTranslateVisitor);
    });
  });
});

describe('geometry literals', function () {
  const translate = (q, dialect) => GremlinTranslator.translate(q, 'g', dialect).getTranslated();

  const point = 'g.inject(Geometry("POINT(1 2)"))';
  const geographicPoint = 'g.inject(Geometry("POINT(13.4 52.5)", GEOGRAPHIC))';
  const sridPoint = 'g.inject(Geometry("POINT(1 2)", 32618))';
  const ewktPoint = 'g.inject(Geometry("SRID=4326;POINT(13.4 52.5)"))';
  const holedSquare = 'g.inject(Geometry("POLYGON((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 1 2, 2 2, 2 1, 1 1))"))';
  const nestedCollection = 'g.inject(Geometry("GEOMETRYCOLLECTION(POINT(1 1), MULTIPOINT EMPTY)"))';
  const pointZ = 'g.inject(Geometry("POINT Z (1 2 3)"))';
  const untaggedPointZ = 'g.inject(Geometry("POINT(1 2 3)"))';
  const untaggedLineStringZ = 'g.inject(Geometry("LINESTRING(0 0 1, 1 1 2)"))';
  const emptyPoint = 'g.inject(Geometry("POINT EMPTY"))';
  const negativeZero = 'g.inject(Geometry("POINT(-0 0)"))';
  const otherModel = 'g.inject(Model.OTHER)';
  const geographicModel = 'g.inject(Model.GEOGRAPHIC)';

  const tests = [
    // the gremlin-lang dialect writes the literal as it was given, with whitespace normalized
    ['CANONICAL', point, 'g.inject(Geometry("POINT(1 2)"))'],
    ['CANONICAL', geographicPoint, 'g.inject(Geometry("POINT(13.4 52.5)", GEOGRAPHIC))'],
    // the JVM languages read the WKT back
    ['JAVA', point, 'g.inject(Geometry.fromWkt("POINT(1 2)"))'],
    ['JAVA', geographicPoint, 'g.inject(Geometry.fromWkt("POINT(13.4 52.5)", Model.GEOGRAPHIC))'],
    ['JAVA', sridPoint, 'g.inject(Geometry.fromWkt("POINT(1 2)", 32618))'],
    ['JAVA', ewktPoint, 'g.inject(Geometry.fromWkt("POINT(13.4 52.5)", Model.GEOGRAPHIC))'],
    ['JAVA', pointZ, 'g.inject(Geometry.fromWkt("POINT Z (1 2 3)"))'],
    ['GROOVY', emptyPoint, 'g.inject(Geometry.fromWkt("POINT EMPTY"))'],
    // the GLVs build the value from the constructors of their kinds
    ['JAVASCRIPT', point, 'g.inject(Point.of(1.0, 2.0))'],
    ['JAVASCRIPT', geographicPoint, 'g.inject(Point.of(13.4, 52.5, Model.GEOGRAPHIC))'],
    ['JAVASCRIPT', sridPoint, 'g.inject(Point.of(1.0, 2.0, 32618))'],
    // an SRID may be written in any integer width and in hexadecimal, octal or decimal form
    ['JAVASCRIPT', 'g.inject(Geometry("POINT(1 2)", 32618N))', 'g.inject(Point.of(1.0, 2.0, 32618))'],
    ['JAVASCRIPT', 'g.inject(Geometry("POINT(1 2)", 32_618))', 'g.inject(Point.of(1.0, 2.0, 32618))'],
    ['JAVASCRIPT', 'g.inject(Geometry("POINT(1 2)", 0x7F5A))', 'g.inject(Point.of(1.0, 2.0, 32602))'],
    ['JAVASCRIPT', 'g.inject(Geometry("POINT(1 2)", 017))', 'g.inject(Point.of(1.0, 2.0, 15))'],
    ['JAVASCRIPT', 'g.inject(Geometry("POINT(1 2)", -32618))', 'g.inject(Point.of(1.0, 2.0, -32618))'],
    ['JAVASCRIPT', pointZ, 'g.inject(Point.ofZ(1.0, 2.0, 3.0))'],
    ['JAVASCRIPT', emptyPoint, 'g.inject(Point.empty())'],
    ['JAVASCRIPT', negativeZero, 'g.inject(Point.of(-0.0, 0.0))'],
    ['JAVASCRIPT', holedSquare,
      'g.inject(Polygon.of(LineString.of(Point.of(0.0, 0.0), Point.of(4.0, 0.0), Point.of(4.0, 4.0), ' +
      'Point.of(0.0, 4.0), Point.of(0.0, 0.0)), LineString.of(Point.of(1.0, 1.0), Point.of(1.0, 2.0), ' +
      'Point.of(2.0, 2.0), Point.of(2.0, 1.0), Point.of(1.0, 1.0))))'],
    ['JAVASCRIPT', nestedCollection, 'g.inject(GeometryCollection.of(Point.of(1.0, 1.0), MultiPoint.empty()))'],
    ['PYTHON', point, 'g.inject(Point(1.0, 2.0))'],
    ['PYTHON', geographicPoint, 'g.inject(Point(13.4, 52.5, model=Model.GEOGRAPHIC))'],
    ['PYTHON', sridPoint, 'g.inject(Point(1.0, 2.0, srid=32618))'],
    ['PYTHON', holedSquare,
      'g.inject(Polygon(LineString([Point(0.0, 0.0), Point(4.0, 0.0), Point(4.0, 4.0), Point(0.0, 4.0), ' +
      'Point(0.0, 0.0)]), [LineString([Point(1.0, 1.0), Point(1.0, 2.0), Point(2.0, 2.0), Point(2.0, 1.0), ' +
      'Point(1.0, 1.0)])]))'],
    ['PYTHON', nestedCollection, 'g.inject(GeometryCollection([Point(1.0, 1.0), MultiPoint.empty()]))'],
    ['DOTNET', point, 'g.Inject<object>(new Point(1.0, 2.0))'],
    ['DOTNET', geographicPoint, 'g.Inject<object>(new Point(13.4, 52.5, Model.Geographic))'],
    ['DOTNET', sridPoint, 'g.Inject<object>(Point.WithSrid(1.0, 2.0, 32618))'],
    ['DOTNET', emptyPoint, 'g.Inject<object>(Point.Empty())'],
    ['GO', point, 'g.Inject(gremlingo.MustNewPoint(1.0, 2.0, gremlingo.Model.Cartesian))'],
    ['GO', geographicPoint, 'g.Inject(gremlingo.MustNewPoint(13.4, 52.5, gremlingo.Model.Geographic))'],
    ['GO', sridPoint, 'g.Inject(gremlingo.MustNewPointWithSrid(1.0, 2.0, 32618))'],
    ['GO', emptyPoint, 'g.Inject(gremlingo.MustNewEmptyPoint(gremlingo.Model.Cartesian))'],
    ['GO', 'g.inject(Geometry("POINT EMPTY", 32618))', 'g.Inject(gremlingo.NewEmptyPointWithSrid(32618))'],
    ['GO', negativeZero, 'g.Inject(gremlingo.MustNewPoint(math.Copysign(0, -1), 0.0, gremlingo.Model.Cartesian))'],
    // a Model value on its own, which each GLV renders as it names the enum entry
    ['JAVASCRIPT', otherModel, 'g.inject(Model.OTHER)'],
    ['JAVASCRIPT', geographicModel, 'g.inject(Model.GEOGRAPHIC)'],
    ['PYTHON', otherModel, 'g.inject(Model.OTHER)'],
    ['PYTHON', geographicModel, 'g.inject(Model.GEOGRAPHIC)'],
    ['DOTNET', otherModel, 'g.Inject<object>(Model.Other)'],
    ['DOTNET', geographicModel, 'g.Inject<object>(Model.Geographic)'],
    ['GO', otherModel, 'g.Inject(gremlingo.Model.Other)'],
    ['GO', geographicModel, 'g.Inject(gremlingo.Model.Geographic)'],
    // the anonymizer replaces a geometry with a placeholder named for the kind its WKT leads with
    ['ANONYMIZED', point, 'g.inject(point0)'],
    ['ANONYMIZED', 'g.inject(Geometry("POINT(1 2)"), Geometry("POINT(3 4)"), Geometry("POINT(1 2)"))',
      'g.inject(point0, point1, point0)'],
    // PostGIS EWKT writes a 3D value with no Z tag, which the reader takes as 3D. The JVM dialects echo the
    // WKT as given, where the Java translator writes back the ISO form it reads.
    ['CANONICAL', untaggedPointZ, 'g.inject(Geometry("POINT(1 2 3)"))'],
    ['JAVA', untaggedPointZ, 'g.inject(Geometry.fromWkt("POINT(1 2 3)"))'],
    ['GROOVY', untaggedPointZ, 'g.inject(Geometry.fromWkt("POINT(1 2 3)"))'],
    ['JAVASCRIPT', untaggedPointZ, 'g.inject(Point.ofZ(1.0, 2.0, 3.0))'],
    ['PYTHON', untaggedPointZ, 'g.inject(Point(1.0, 2.0, 3.0))'],
    ['DOTNET', untaggedPointZ, 'g.Inject<object>(new Point(1.0, 2.0, 3.0))'],
    ['GO', untaggedPointZ, 'g.Inject(gremlingo.MustNewPointZ(1.0, 2.0, 3.0, gremlingo.Model.Cartesian))'],
    ['ANONYMIZED', untaggedPointZ, 'g.inject(point0)'],
    ['CANONICAL', untaggedLineStringZ, 'g.inject(Geometry("LINESTRING(0 0 1, 1 1 2)"))'],
    ['JAVA', untaggedLineStringZ, 'g.inject(Geometry.fromWkt("LINESTRING(0 0 1, 1 1 2)"))'],
    ['GROOVY', untaggedLineStringZ, 'g.inject(Geometry.fromWkt("LINESTRING(0 0 1, 1 1 2)"))'],
    ['JAVASCRIPT', untaggedLineStringZ, 'g.inject(LineString.of(Point.ofZ(0.0, 0.0, 1.0), Point.ofZ(1.0, 1.0, 2.0)))'],
    ['PYTHON', untaggedLineStringZ, 'g.inject(LineString([Point(0.0, 0.0, 1.0), Point(1.0, 1.0, 2.0)]))'],
    ['DOTNET', untaggedLineStringZ,
      'g.Inject<object>(new LineString(new Point(0.0, 0.0, 1.0), new Point(1.0, 1.0, 2.0)))'],
    ['GO', untaggedLineStringZ,
      'g.Inject(gremlingo.MustNewLineString(gremlingo.MustNewPointZ(0.0, 0.0, 1.0, gremlingo.Model.Cartesian), ' +
      'gremlingo.MustNewPointZ(1.0, 1.0, 2.0, gremlingo.Model.Cartesian)))'],
    ['ANONYMIZED', untaggedLineStringZ, 'g.inject(linestring0)'],
  ];

  tests.forEach(function ([dialect, input, expected]) {
    it(`[${dialect.toLowerCase()}] translates: ${input}`, function () {
      assert.strictEqual(translate(input, dialect), expected);
    });
  });

  describe('rejections', function () {
    it('throws TranslatorException for an empty 3D geometry in a GLV', function () {
      ['JAVASCRIPT', 'PYTHON', 'DOTNET', 'GO'].forEach(function (dialect) {
        assert.throws(
          () => translate('g.inject(Geometry("POINT Z EMPTY"))', dialect),
          TranslatorException,
          `${dialect} should not construct an empty 3D geometry`,
        );
      });
    });

    it('reads an empty 3D geometry back from its WKT in the JVM languages', function () {
      assert.strictEqual(translate('g.inject(Geometry("POINT Z EMPTY"))', 'JAVA'),
        'g.inject(Geometry.fromWkt("POINT Z EMPTY"))');
    });

    it('throws TranslatorException for malformed WKT', function () {
      assert.throws(() => translate('g.inject(Geometry("POINT(1)"))', 'JAVASCRIPT'), TranslatorException);
      assert.throws(() => translate('g.inject(Geometry("CIRCULARSTRING(1 1, 2 2, 3 3)"))', 'JAVASCRIPT'),
        TranslatorException);
      assert.throws(() => translate('g.inject(Geometry("POLYGON((0 0, 1 0, 1 1))"))', 'JAVASCRIPT'),
        TranslatorException);
    });

    it('throws TranslatorException when an SRID prefix conflicts with the argument', function () {
      assert.throws(() => translate('g.inject(Geometry("SRID=4326;POINT(1 2)", CARTESIAN))', 'JAVASCRIPT'),
        /Invalid Geometry literal: EWKT SRID 4326 conflicts with model CARTESIAN/);
    });

    it('throws TranslatorException for an SRID outside the 32-bit range', function () {
      assert.throws(() => translate('g.inject(Geometry("POINT(1 2)", 99999999999999))', 'JAVASCRIPT'),
        /Invalid Geometry literal: SRID is out of range for a 32-bit integer/);
    });
  });
});

describe('geo predicates', function () {
  const translate = (q, dialect) => GremlinTranslator.translate(q, 'g', dialect).getTranslated();

  const within = 'g.V().is(geoWithin(Geometry("POINT(1 2)")))';
  const contains = 'g.V().is(GeoP.geoContains(Geometry("POINT(1 2)")))';
  const intersects = 'g.V().is(geoIntersects(Geometry("POINT(1 2)")))';
  const disjoint = 'g.V().is(GeoP.geoDisjoint(Geometry("POINT(1 2)")))';
  const equals = 'g.V().is(geoEquals(Geometry("POINT(1 2)")))';
  const fromTraversal = 'g.V().values("loc").is(geoWithin(__.select("v").values("zone")))';

  const tests = [
    ['CANONICAL', within, 'g.V().is(GeoP.geoWithin(Geometry("POINT(1 2)")))'],
    ['CANONICAL', intersects, 'g.V().is(GeoP.geoIntersects(Geometry("POINT(1 2)")))'],
    ['CANONICAL', equals, 'g.V().is(GeoP.geoEquals(Geometry("POINT(1 2)")))'],
    ['JAVA', within, 'g.V().is(GeoP.geoWithin(Geometry.fromWkt("POINT(1 2)")))'],
    ['JAVA', disjoint, 'g.V().is(GeoP.geoDisjoint(Geometry.fromWkt("POINT(1 2)")))'],
    ['JAVASCRIPT', within, 'g.V().is(GeoP.geoWithin(Point.of(1.0, 2.0)))'],
    ['JAVASCRIPT', contains, 'g.V().is(GeoP.geoContains(Point.of(1.0, 2.0)))'],
    ['JAVASCRIPT', intersects, 'g.V().is(GeoP.geoIntersects(Point.of(1.0, 2.0)))'],
    ['JAVASCRIPT', disjoint, 'g.V().is(GeoP.geoDisjoint(Point.of(1.0, 2.0)))'],
    ['JAVASCRIPT', equals, 'g.V().is(GeoP.geoEquals(Point.of(1.0, 2.0)))'],
    ['JAVASCRIPT', fromTraversal, 'g.V().values("loc").is(GeoP.geoWithin(__.select("v").values("zone")))'],
    ['PYTHON', within, "g.V().is_(GeoP.geo_within(Point(1.0, 2.0)))"],
    ['PYTHON', intersects, "g.V().is_(GeoP.geo_intersects(Point(1.0, 2.0)))"],
    ['DOTNET', within, 'g.V().Is(GeoP.GeoWithin(new Point(1.0, 2.0)))'],
    ['DOTNET', disjoint, 'g.V().Is(GeoP.GeoDisjoint(new Point(1.0, 2.0)))'],
    ['GO', within,
      'g.V().Is(gremlingo.GeoP.GeoWithin(gremlingo.MustNewPoint(1.0, 2.0, gremlingo.Model.Cartesian)))'],
    ['GO', equals,
      'g.V().Is(gremlingo.GeoP.GeoEquals(gremlingo.MustNewPoint(1.0, 2.0, gremlingo.Model.Cartesian)))'],
    ['ANONYMIZED', within, 'g.V().is(GeoP.geoWithin(point0))'],
    ['ANONYMIZED', intersects, 'g.V().is(GeoP.geoIntersects(point0))'],
  ];

  tests.forEach(function ([dialect, input, expected]) {
    it(`[${dialect.toLowerCase()}] translates: ${input}`, function () {
      assert.strictEqual(translate(input, dialect), expected);
    });
  });
});
