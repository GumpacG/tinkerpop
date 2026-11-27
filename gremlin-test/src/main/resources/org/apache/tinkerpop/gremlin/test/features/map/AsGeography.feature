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

@StepClassMap @StepAsGeography @DataGeometry
Feature: Step - asGeography()

  # Eli keeps a position as a map of named coordinates, and the lon and lat keys are read as longitude and latitude.

  @AllowGeometryPropertyValues @AllowMapPropertyValues @DataMap
  Scenario: g_V_hasXname_EliX_valuesXcoordsX_asGeography
    Given the delivery graph
    And the intent
      """
      What position do the named coordinates Eli keeps describe, in longitude and latitude?
      """
    And the traversal of
      """
      g.V().has("name", "Eli").values("coords").asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT(-74.05 40.72)] |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_HanaX_valuesXrawX_asGeography
    Given the delivery graph
    And the intent
      """
      What position does the WKT string Hana keeps her raw position as describe, in longitude and latitude?
      """
    And the traversal of
      """
      g.V().has("name", "Hana").values("raw").asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT(0.012 51.481)] |

  # The picker is placed on the warehouse floor in meters, and 3 4 happens to be a longitude and a latitude too, so
  # relabelling it is allowed and the coordinates are kept as they are.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_asGeography
    Given the delivery graph
    And the intent
      """
      Read the picker's position as longitude and latitude rather than as a position on the plane.
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT(3 4)] |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_ChilledX_valuesXlocX_asGeography
    Given the delivery graph
    And the intent
      """
      Read the outline of the Chilled room as longitude and latitude rather than as a shape on the plane.
      """
    And the traversal of
      """
      g.V().has("name", "Chilled").values("loc").asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POLYGON((0 0, 8 0, 8 10, 0 10, 0 0))] |

  # Ana's position is already in longitude and latitude, so this is the case where the step has nothing to change and
  # gives the position back as it is, with its z.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AnaX_valuesXlocX_asGeography
    Given the delivery graph
    And the intent
      """
      Read Ana's position as longitude and latitude.
      """
    And the traversal of
      """
      g.V().has("name", "Ana").values("loc").asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT Z (-73.9857 40.7484 86)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXx_yXX_asGeography
    Given the empty graph
    And the intent
      """
      The x and y keys name a position just as lon and lat do, and asGeography() reads them as longitude and latitude.
      """
    And the traversal of
      """
      g.inject([x: 1, y: 2]).asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[SRID=4326;POINT(1 2)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXnullX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject(null).asGeography()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | null |

  # The coordinates of web mercator are meters on a projected plane, not longitude and latitude, so there is nothing
  # the step could read them as.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPointX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(1 2)", 3857)).asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() cannot relabel a geometry with SRID 3857 as GEOGRAPHIC"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorEwktX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject("SRID=3857;POINT(1 2)").asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() cannot relabel a geometry with SRID 3857 as GEOGRAPHIC"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXbeyondLongitudeRangeX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(200 0)")).asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() cannot read the coordinates: GEOGRAPHIC longitude must be in [-180, 180]"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlonBeyondRangeXX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject([lon: 200, lat: 0]).asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() cannot read the coordinates: GEOGRAPHIC longitude must be in [-180, 180]"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlatBeyondRangeXX_asGeography
    Given the empty graph
    And the traversal of
      """
      g.inject([lon: 0, lat: 100]).asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() cannot read the coordinates: GEOGRAPHIC latitude must be in [-90, 90]"

  # A string the step cannot read as WKT or EWKT is reported under the step name, with the message of the reader
  # carried in the text.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXnameX_asGeography
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("name").asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() could not read the string as WKT or EWKT"

  # A vertex is not a position and is not a value the step reads a position from.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_asGeography
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").asGeography()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeography() can only take a Geometry, a WKT or EWKT string, or a map of named coordinates as input"
