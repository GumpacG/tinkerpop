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

@StepClassMap @StepDistance @DataGeometry
Feature: Step - distance()

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXcartesianOriginX
    Given the delivery graph
    And the intent
      """
      How far is the picker from the position 0 0 on the warehouse floor, in meters?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(Geometry("POINT(0 0)"))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[5.0].d |

  # The picker stands inside the Chilled room, and a shape that holds a position is no distance from it.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXchilledRoomX
    Given the delivery graph
    And the intent
      """
      How far is the picker from the Chilled room, in meters?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(Geometry("POLYGON((0 0, 8 0, 8 10, 0 10, 0 0))"))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[0.0].d |

  # The warehouse rooms and the picker are CARTESIAN geometries in meters, and the Ambient room begins 5 meters east of
  # where the picker stands.

  @AllowGeometryPropertyValues @GraphComputerVerificationStarGraphExceeded
  Scenario: g_V_hasXpicker_name_PatX_asXpX_valuesXlocX_distanceXselectXpX_outXworksInX_inXhasRoomX_outXhasRoomX_hasXname_AmbientX_valuesXlocXX
    Given the delivery graph
    And the intent
      """
      How far is the picker from the other room of their hub, in meters?
      """
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").as("p").values("loc").distance(__.select("p").out("worksIn").in("hasRoom").out("hasRoom").has("name", "Ambient").values("loc"))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[5.0].d |

  # A GEOGRAPHIC measure is taken on a sphere and is within about 1% of the true figure, so the band is the one percent
  # either side of the 5575.3 kilometers the edge records.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXhub_name_HellsKitchenX_valuesXlocX_distanceXgreenwichX_mathXdivX1000X_isXinsideX_count
    Given the delivery graph
    And the intent
      """
      The shipsTo edge from Hell's Kitchen to Greenwich records 5575.3 great-circle kilometers. Count the hub whose
      measured distance to Greenwich falls within one percent of that.
      """
    And the traversal of
      """
      g.V().has("hub", "name", "Hell's Kitchen").values("loc").distance(Geometry("POINT(0 51.48)", GEOGRAPHIC)).math("_ / 1000").is(P.inside(5519.5, 5631.1)).count()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[1].l |

  @AllowGeometryPropertyValues @GraphComputerVerificationReferenceOnly
  Scenario: g_E_hasLabelXshipsToX_asXsX_outV_valuesXlocX_distanceXselectXsX_inV_valuesXlocXX_mathXdivXkmX_isXinsideX_count
    Given the delivery graph
    And the intent
      """
      Every shipsTo edge records the great-circle distance between its two hubs in kilometers. How many of them have a
      measured distance within one percent of what they record?
      """
    And the traversal of
      """
      g.E().hasLabel("shipsTo").as("s").outV().values("loc").distance(__.select("s").inV().values("loc")).as("measured").select("s").values("km").as("recorded").math("measured / recorded").is(P.inside(990.0, 1010.0)).count()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[4].l |

  @AllowGeometryPropertyValues
  Scenario: g_V_hasLabelXhubX_order_byXvaluesXlocX_distanceXmidtownPointXX_limitX3X_valuesXnameX
    Given the delivery graph
    And the intent
      """
      Which three hubs are nearest to the position -73.98 40.75, in order?
      """
    And the traversal of
      """
      g.V().hasLabel("hub").order().by(__.values("loc").distance(Geometry("POINT(-73.98 40.75)", GEOGRAPHIC))).limit(3).values("name")
      """
    When iterated to list
    Then the result should be ordered
      | result |
      | Hell's Kitchen |
      | Williamsburg |
      | Los Angeles |

  # Dee lives 3840 meters from the nearest corner of the Congestion Zone, and the band is the one percent either side
  # of that.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXcustomer_name_DeeX_valuesXlocX_distanceXcongestionZoneX_isXinsideX_count
    Given the delivery graph
    And the intent
      """
      Count the customer Dee when her measured distance to the Congestion Zone falls within one percent of the 3840
      meters she lives from it.
      """
    And the traversal of
      """
      g.V().has("customer", "name", "Dee").values("loc").distance(Geometry("POLYGON((-74.02 40.7, -73.975 40.7, -73.975 40.7644, -74.02 40.7644, -74.02 40.7))", GEOGRAPHIC)).is(P.inside(3802.0, 3879.0)).count()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[1].l |

  # A degree of longitude covers less ground than a degree of latitude here, so reading the edge in raw degrees would
  # put the nearest position elsewhere on it and give 427.8 meters, which the band excludes.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXcustomer_name_BenX_valuesXlocX_distanceXmidtownX_isXinsideX_count
    Given the delivery graph
    And the intent
      """
      Ben stands in the park hole of the Midtown zone, so his distance to that zone is his distance to the hole, whose
      nearest position lies 413.1 meters away partway along one of its diagonal edges. Count the customer whose
      measured distance falls within one percent of that.
      """
    And the traversal of
      """
      g.V().has("customer", "name", "Ben").values("loc").distance(Geometry("POLYGON((-74.02 40.74, -73.93 40.74, -73.93 40.81, -74.02 40.81, -74.02 40.74), (-73.9819 40.7681, -73.973 40.7644, -73.9498 40.7968, -73.958 40.8006, -73.9819 40.7681))", GEOGRAPHIC)).is(P.inside(409.0, 417.3)).count()
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[1].l |

  # The hub stands inside the zone it serves, and a shape that holds a position is no distance from it, so the figure
  # is exactly zero rather than a small measured number.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXhub_name_GreenwichX_valuesXlocX_distanceXgreenwichTownX
    Given the delivery graph
    And the intent
      """
      How far is the Greenwich hub from the Greenwich Town zone, in meters?
      """
    And the traversal of
      """
      g.V().has("hub", "name", "Greenwich").values("loc").distance(Geometry("POLYGON((-0.03 51.46, 0.03 51.46, 0.03 51.5, -0.03 51.5, -0.03 51.46))", GEOGRAPHIC))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[0.0].d |

  # Distance is measured in 2D, so the 86 meter z of Ana's position is ignored.

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXname_AnaX_valuesXlocX_distanceXanaPositionX
    Given the delivery graph
    And the intent
      """
      How far is Ana from the position at her own longitude and latitude, in meters?
      """
    And the traversal of
      """
      g.V().has("name", "Ana").values("loc").distance(Geometry("POINT(-73.9857 40.7484)", GEOGRAPHIC))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | d[0.0].d |

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXnullX_distanceXcartesianOriginX
    Given the empty graph
    And the traversal of
      """
      g.inject(null).distance(Geometry("POINT(0 0)"))
      """
    When iterated to list
    Then the result should be unordered
      | result |
      | null |

  @AllowGeometryPropertyValues @AllowMixedGeometryModels
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXgeographicHubX
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(Geometry("POINT(-73.9925 40.7614)", GEOGRAPHIC))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Distance needs both geometries in the same model, got CARTESIAN and GEOGRAPHIC"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPointX_distanceXwebMercatorPointX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)", 3857)).distance(Geometry("POINT(3 4)", 3857))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Distance is not defined for SRID 3857"

  # A reference system the step does not measure is reported for either geometry, by the SRID that names it, ahead of
  # the two geometries being in different models.

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXwebMercatorPointX_distanceXcartesianPointX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)", 3857)).distance(Geometry("POINT(3 4)"))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Distance is not defined for SRID 3857"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXPOINT_EMPTYX_distanceXcartesianPointX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT EMPTY")).distance(Geometry("POINT(3 4)"))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Geometry to measure from cannot be empty"

  @GraphComputerVerificationInjectionNotSupported
  Scenario: g_injectXcartesianOriginX_distanceXPOINT_EMPTYX
    Given the empty graph
    And the traversal of
      """
      g.inject(Geometry("POINT(0 0)")).distance(Geometry("POINT EMPTY"))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "Geometry to measure to cannot be empty"

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXlimitX0XX
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(__.limit(0))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "distance() needs a geometry from"

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXconstantXnullXX
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(__.constant(null))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "distance() needs a Geometry from its traversal, encountered null"

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXlocX_distanceXconstantXstringXX
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("loc").distance(__.constant("POINT(0 0)"))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "distance() needs a Geometry from its traversal, encountered"

  @AllowGeometryPropertyValues
  Scenario: g_V_hasXpicker_name_PatX_valuesXnameX_distanceXcartesianOriginX
    Given the delivery graph
    And the traversal of
      """
      g.V().has("picker", "name", "Pat").values("name").distance(Geometry("POINT(0 0)"))
      """
    When iterated to list
    Then the traversal will raise an error with message containing text of "distance() can only be applied to a Geometry"
