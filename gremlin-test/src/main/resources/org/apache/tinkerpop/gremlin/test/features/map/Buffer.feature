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

@StepClassMap @StepBuffer @DataGeometry
Feature: Step - buffer()

  # Every scenario that measures a region brackets it with two probes in one scenario, a near one inside the distance
  # asked for and a far one just outside it, so that neither an empty region nor one of the wrong size can pass.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_bufferX1_0X_projectXnear_farX_byXisXnearPatX_countX_byXisXfarPatX_countX
    Given the delivery graph
    And the intent
      """
      Does the region within one meter of the picker hold a position 0.9 meters from them, and one 1.1 meters from
      them? Return a map of near and far, counting 1 where the region holds the position and 0 where it does not.
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").buffer(1.0).project("near", "far").by(__.is(GeoP.geoContains(Geometry("POINT(3.9 4)"))).count()).by(__.is(GeoP.geoContains(Geometry("POINT(4.1 4)"))).count())
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | m[{"near":"d[1].l","far":"d[0].l"}] |

  # The expected ring is the one JTS writes for that rectangle, in the order and from the starting corner it chooses.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_ChilledX_valuesXlocX_bufferXminus1_0X
    Given the delivery graph
    And the intent
      """
      What is the region of the Chilled room that lies at least one meter inside its walls?
      """
    And the traversal of
      """
      g.V().has("name", "Chilled").values("loc").buffer(-1.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON((1 1, 1 9, 7 9, 7 1, 1 1))] |

  # A position has no area to shrink, so a negative distance leaves nothing.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_bufferXminus1_0X
    Given the delivery graph
    And the intent
      """
      What is the region of the picker's position that lies at least one meter inside it?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").buffer(-1.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON EMPTY] |

  # A buffer of zero asks for the area of the geometry itself, and a position has none.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_bufferX0_0X
    Given the delivery graph
    And the intent
      """
      What is the region within zero meters of the picker's position?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").buffer(0.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON EMPTY] |

  # A line has no area to shrink either.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXcartesianLineX_bufferXminus1_0X
    Given the empty graph
    And the intent
      """
      What is the region of the CARTESIAN line from 0 0 to 8 0 that lies at least one meter inside it?
      """
    And the traversal of
      """
      g.inject(Geometry("LINESTRING(0 0, 8 0)")).buffer(-1.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON EMPTY] |

  # A buffer of zero in the GEOGRAPHIC model behaves as it does in the CARTESIAN model, and keeps the SRID of the
  # position it was asked around.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXgeographicPointX_bufferX0_0X
    Given the empty graph
    And the intent
      """
      What is the region within zero meters of the position 0 51.48 in longitude and latitude?
      """
    And the traversal of
      """
      g.inject(Geometry("POINT(0 51.48)", GEOGRAPHIC)).buffer(0.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POLYGON EMPTY] |

  # The near probe lies 890 meters due north of the hub and the far probe 1045 meters due north of it, both measured on
  # a great circle, far enough either side of the kilometer to clear the 0.5% margin of the 32-position cap.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXhub_name_GreenwichX_valuesXlocX_bufferX1000_0X_projectXnear_farX_byXisXnearGreenwichX_countX_byXisXfarGreenwichX_countX
    Given the delivery graph
    And the intent
      """
      Does the region within one kilometer of the Greenwich hub hold a position 890 meters north of it, and one 1045
      meters north of it? Return a map of near and far, counting 1 where the region holds the position and 0 where it
      does not.
      """
    And the traversal of
      """
      g.V().has("hub", "name", "Greenwich").values("loc").buffer(1000.0).project("near", "far").by(__.is(GeoP.geoContains(Geometry("POINT(0 51.488)", GEOGRAPHIC))).count()).by(__.is(GeoP.geoContains(Geometry("POINT(0 51.4894)", GEOGRAPHIC))).count())
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | m[{"near":"d[1].l","far":"d[0].l"}] |

  # Both probes sit at a right angle to the midpoint of the delivery path, the near one 300 meters from it and the far
  # one 525 meters from it, measured on a great circle.

  @AllowGeometryPropertyValues
  Scenario: g_E_hasLabelXdeliveredX_valuesXpathX_bufferX500_0X_projectXnear_farX_byXisXnearPathX_countX_byXisXfarPathX_countX
    Given the delivery graph
    And the intent
      """
      Does the region within 500 meters of the path the delivery took hold a position 300 meters from that path, and
      one 525 meters from it? Return a map of near and far, counting 1 where the region holds the position and 0 where
      it does not.
      """
    And the traversal of
      """
      g.E().hasLabel("delivered").values("path").buffer(500.0).project("near", "far").by(__.is(GeoP.geoContains(Geometry("POINT(-73.968402 40.732956)", GEOGRAPHIC))).count()).by(__.is(GeoP.geoContains(Geometry("POINT(-73.966154 40.734048)", GEOGRAPHIC))).count())
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | m[{"near":"d[1].l","far":"d[0].l"}] |

  # A buffer grows a shape outward from every edge it has, including the edges of a hole, so the park hole of the
  # Midtown zone is filled in from its rim. The near probe lies 150 meters inside the hole from the midpoint of its
  # long south-eastern edge; the far probe is where Ben stands, 413 meters from the rim, the deepest the hole gets.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_MidtownX_valuesXlocX_bufferX300_0X_projectXnear_farX_byXisXnearHoleRimX_countX_byXisXholeCentreX_countX
    Given the delivery graph
    And the intent
      """
      Does the region within 300 meters of the Midtown zone hold a position 150 meters inside the park hole of that
      zone, and the middle of that hole? Return a map of near and far, counting 1 where the region holds the position
      and 0 where it does not.
      """
    And the traversal of
      """
      g.V().has("name", "Midtown").values("loc").buffer(300.0).project("near", "far").by(__.is(GeoP.geoContains(Geometry("POINT(-73.962966 40.781243)", GEOGRAPHIC))).count()).by(__.is(GeoP.geoContains(Geometry("POINT(-73.9654 40.7829)", GEOGRAPHIC))).count())
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | m[{"near":"d[1].l","far":"d[0].l"}] |

  # A buffer is drawn in 2D, so the 86 meter z of Ana's position is dropped and the region is a flat one. The probes
  # lie 500 meters and 1050 meters north-east of her on a great circle, and neither has a z of its own.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AnaX_valuesXlocX_bufferX1000_0X_projectXnear_farX_byXisXnearAnaX_countX_byXisXfarAnaX_countX
    Given the delivery graph
    And the intent
      """
      Does the region within one kilometer of Ana hold a position 500 meters north-east of her, and one 1050 meters
      north-east of her? Return a map of near and far, counting 1 where the region holds the position and 0 where it
      does not.
      """
    And the traversal of
      """
      g.V().has("name", "Ana").values("loc").buffer(1000.0).project("near", "far").by(__.is(GeoP.geoContains(Geometry("POINT(-73.981503 40.75158)", GEOGRAPHIC))).count()).by(__.is(GeoP.geoContains(Geometry("POINT(-73.976885 40.755077)", GEOGRAPHIC))).count())
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | m[{"near":"d[1].l","far":"d[0].l"}] |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXhub_name_GreenwichX_valuesXlocX_bufferXminus1000_0X
    Given the delivery graph
    And the traversal of
      """
      g.V().has("hub", "name", "Greenwich").values("loc").buffer(-1000.0)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Buffer distance in the GEOGRAPHIC model cannot be negative"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXPOINT_EMPTYX_bufferX5_0X
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT EMPTY")).buffer(5.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON EMPTY] |

  # An empty geometry is answered before any rule about the distance beyond its being a finite number, so the negative
  # distance a GEOGRAPHIC buffer otherwise refuses passes here, and the SRID of the geometry is kept.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXgeographicPOLYGON_EMPTYX_bufferXminus1000_0X
    Given the empty graph
    And the intent
      """
      What is the region of an empty geometry in longitude and latitude that lies at least one kilometer inside it?
      """
    And the traversal of
      """
      g.inject(Geometry("POLYGON EMPTY", GEOGRAPHIC)).buffer(-1000.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POLYGON EMPTY] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXnullX_bufferX1_0X
    Given the empty graph
    And the traversal of
      """
      g.inject(null).buffer(1.0)
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | null |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXcartesianOriginX_bufferXNaNX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)")).buffer(NaN)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Buffer distance must be a finite number"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXcartesianOriginX_bufferXInfinityX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)")).buffer(Infinity)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Buffer distance must be a finite number"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPointX_bufferX1_0X
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)", 3857)).buffer(1.0)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Buffer is not defined for SRID 3857"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXpoleNeighbourX_bufferX5000_0X
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 89.99)", GEOGRAPHIC)).buffer(5000.0)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "reaches a pole, which a buffer in longitude and latitude does not represent"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXantimeridianNeighbourX_bufferX5000_0X
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(179.99 0)", GEOGRAPHIC)).buffer(5000.0)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "crosses the antimeridian, which a buffer in longitude and latitude does not represent"

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXnameX_bufferX1_0X
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("name").buffer(1.0)
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "buffer() can only be applied to a Geometry"
