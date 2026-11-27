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

@StepClassMap @StepEnvelope @DataGeometry
Feature: Step - envelope()

  # Every expected ring here is the one JTS writes for the box, in the order and from the starting corner it chooses.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_ChilledX_valuesXlocX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of the Chilled room?
      """
    And the traversal of
      """
      g.V().has("name", "Chilled").values("loc").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON((0 0, 0 10, 8 10, 8 0, 0 0))] |

  # The Ambient room is a polygon with a hole, and the box around it ignores the hole.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AmbientX_valuesXlocX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of the Ambient room?
      """
    And the traversal of
      """
      g.V().has("name", "Ambient").values("loc").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POLYGON((8 0, 8 10, 20 10, 20 0, 8 0))] |

  # The box around a single position is that position.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of the picker's position?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(3 4)] |

  # The box is always 2D, so the z of Ana's position is dropped.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AnaX_valuesXlocX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of Ana's position?
      """
    And the traversal of
      """
      g.V().has("name", "Ana").values("loc").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT(-73.9857 40.7484)] |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_MidtownX_valuesXlocX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of the Midtown zone?
      """
    And the traversal of
      """
      g.V().has("name", "Midtown").values("loc").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POLYGON((-74.02 40.74, -74.02 40.81, -73.93 40.81, -73.93 40.74, -74.02 40.74))] |

  # A GEOGRAPHIC box is computed in longitude and latitude, so the shipping path from Los Angeles to Auckland, which is
  # split at the antimeridian, gives a box reaching from -180 to 180.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXhub_name_LosAngelesX_outEXshipsToX_valuesXpathX_envelope
    Given the delivery graph
    And the intent
      """
      What is the bounding box of the shipping path out of Los Angeles?
      """
    And the traversal of
      """
      g.V().has("hub", "name", "Los Angeles").outE("shipsTo").values("path").envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POLYGON((-180 -36.8485, -180 34.0522, 180 34.0522, 180 -36.8485, -180 -36.8485))] |

  # Any reference system is accepted, since the box is read off the coordinates and asks nothing of the system they
  # are given in.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorLineStringX_envelope
    Given the empty graph
    And the intent
      """
      What is the bounding box of the line from 0 0 to 2 1 in web mercator, which is SRID 3857?
      """
    And the traversal of
      """
      g.inject(Geometry("LINESTRING(0 0, 2 1)", 3857)).envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=3857;POLYGON((0 0, 0 1, 2 1, 2 0, 0 0))] |

  # A box with no width is a line, not a polygon, so a vertical line gives itself back.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXverticalLineStringX_envelope
    Given the empty graph
    And the intent
      """
      What is the bounding box of the vertical line from 1 1 to 1 5?
      """
    And the traversal of
      """
      g.inject(Geometry("LINESTRING(1 1, 1 5)")).envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[LINESTRING(1 1, 1 5)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXPOLYGON_EMPTYX_envelope
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POLYGON EMPTY")).envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT EMPTY] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXnullX_envelope
    Given the empty graph
    And the traversal of
      """
      g.inject(null).envelope()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | null |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXnameX_envelope
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("name").envelope()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "envelope() can only be applied to a Geometry"
