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

@StepClassMap @StepAsGeometry @DataGeometry
Feature: Step - asGeometry()

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_HanaX_valuesXrawX_asGeometry
    Given the delivery graph
    And the intent
      """
      What geometry does the WKT string Hana keeps her raw position as describe?
      """
    And the traversal of
      """
      g.V().has("name", "Hana").values("raw").asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(0.012 51.481)] |

  # Eli keeps a position as a map of named coordinates, and the lon and lat keys are read as the two coordinates of a
  # geometry on the plane.

  @AllowGeometryPropertyValues @AllowMapPropertyValues @DataMap
  Scenario: g_V_hasXname_EliX_valuesXcoordsX_asGeometry
    Given the delivery graph
    And the intent
      """
      What geometry do the named coordinates Eli keeps describe?
      """
    And the traversal of
      """
      g.V().has("name", "Eli").values("coords").asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(-74.05 40.72)] |

  # The Greenwich hub's position carries no z, so relabelling it gives a 2D geometry.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_GreenwichX_valuesXlocX_asGeometry
    Given the delivery graph
    And the intent
      """
      Read the Greenwich hub's position as a geometry on the plane rather than as longitude and latitude.
      """
    And the traversal of
      """
      g.V().has("name", "Greenwich").values("loc").asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(0 51.48)] |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AnaX_valuesXlocX_asGeometry
    Given the delivery graph
    And the intent
      """
      Read Ana's position as a geometry on the plane rather than as longitude and latitude, keeping the z it carries.
      """
    And the traversal of
      """
      g.V().has("name", "Ana").values("loc").asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT Z (-73.9857 40.7484 86)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPointX_asGeometry
    Given the empty graph
    And the intent
      """
      Read the position 1 2 in web mercator, which is SRID 3857, as a geometry on the plane.
      """
    And the traversal of
      """
      g.inject(Geometry("POINT(1 2)", 3857)).asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(1 2)] |

  # An empty geometry carries no coordinates, so relabelling it only changes the reference system it is written under.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPOINT_EMPTYX_asGeometry
    Given the empty graph
    And the intent
      """
      Read an empty position in web mercator, which is SRID 3857, as a geometry on the plane.
      """
    And the traversal of
      """
      g.inject(Geometry("POINT EMPTY", 3857)).asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT EMPTY] |

  # An EWKT string names its own reference system, which is read as written and then relabelled.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorEwktX_asGeometry
    Given the empty graph
    And the intent
      """
      What geometry on the plane does the EWKT string for the position 1 2 in SRID 3857 describe?
      """
    And the traversal of
      """
      g.inject("SRID=3857;POINT(1 2)").asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT(1 2)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXx_y_zXX_asGeometry
    Given the empty graph
    And the intent
      """
      The x, y and z keys name a position in three dimensions, and asGeometry() reads them as the three coordinates of
      a geometry on the plane.
      """
    And the traversal of
      """
      g.inject([x: 1, y: 2, z: 3]).asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | geo[POINT Z (1 2 3)] |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXnullX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject(null).asGeometry()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | null |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXlistX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([1, 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() can only take a Geometry, a WKT or EWKT string, or a map of named coordinates as input"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXx_NaNXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([x: NaN, y: 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() needs a finite number for the map key"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlon_xXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([lon: 1, x: 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() cannot take the"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlongitude_latitudeXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([longitude: 1, latitude: 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() does not know the map key"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXLon_latXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([Lon: 1, lat: 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() does not know the map key"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlonXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([lon: 1]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() needs both"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXlon_lat_of_listsXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([lon: [1.0], lat: [2.0]]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() needs a number for the map key"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXmapXnumericKeyXX_asGeometry
    Given the empty graph
    And the traversal of
      """
      g.inject([(1): 2]).asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() needs string map keys"

  # A string the step cannot read as WKT or EWKT is reported under the step name, with the message of the reader
  # carried in the text.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXnameX_asGeometry
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("name").asGeometry()
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "asGeometry() could not read the string as WKT or EWKT"
