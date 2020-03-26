/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.platform.example.location.scala.standalone

import java.nio.file.Files

import com.here.hrn.HRN
import com.here.platform.example.location.utils.Visualization.Color
import com.here.platform.example.location.utils.{FileNameHelper, Visualization}
import com.here.platform.location.compilation.heremapcontent.{AttributeAccessor, AttributeAccessors}
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineStringOperations, LineStrings}
import com.here.platform.location.core.graph.{PropertyMap, RangeBasedProperty}
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.{Forward, Vertex, Vertices}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.schema.rib.v2.advanced_navigation_attributes_partition.AdvancedNavigationAttributesPartition
import com.here.schema.rib.v2.common_attributes.SpeedLimitAttribute

/** An example that shows how to compile Road attributes from HERE Map Content on the fly
  * and use them as properties of vertices from the `Optimized Map for Location Library`.
  */
object OnTheFlyCompiledPropertyMapExample extends App {
  case class VertexWithProperty[T](vertex: Vertex, rangeBasedProperties: Seq[RangeBasedProperty[T]])

  val brandenburgerTor = GeoCoordinate(52.516268, 13.377700)
  val radiusInMeters = 1000.0

  val catalogFactory = new StandaloneCatalogFactory()

  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)
    val hereMapContent = optimizedMap.resolveDependency(HRN("hrn:here:data::olp-here:rib-2"))

    val speedLimitAccessor: AttributeAccessor[AdvancedNavigationAttributesPartition, Int] =
      AttributeAccessors
        .forHereMapContentSegmentAnchor[AdvancedNavigationAttributesPartition,
                                        SpeedLimitAttribute,
                                        Int](
          _.speedLimit,
          _.value
        )

    val speedCategoryColor = PropertyMaps.advancedNavigationAttribute(
      optimizedMap,
      "speed-category-color",
      hereMapContent,
      cacheManager,
      speedLimitAccessor.map(s => (s, Visualization.redToYellowGradient(s.toFloat, 0, 60)))
    )

    val proximitySearch = ProximitySearches.vertices(optimizedMap, cacheManager)

    val verticesInRange = proximitySearch.search(brandenburgerTor, radiusInMeters).map(_.element)

    println(s"Number of vertices in range: ${verticesInRange.size}")

    val verticesWithProperties: Iterable[VertexWithProperty[(Int, Color)]] = for {
      vertex <- verticesInRange
      rangeBasedProperties = speedCategoryColor(vertex)
    } yield VertexWithProperty(vertex, rangeBasedProperties)

    val geometryPropertyMap = PropertyMaps.geometry(optimizedMap, cacheManager)

    serializeToGeoJson(verticesWithProperties, geometryPropertyMap)
  } finally {
    catalogFactory.terminate()
  }

  private def serializeToGeoJson[LS: LineStringOperations](
      crossingSegments: Iterable[VertexWithProperty[(Int, Color)]],
      geometry: PropertyMap[Vertex, LS]): Unit = {
    import au.id.jazzy.play.geojson._
    import com.here.platform.example.location.utils.Visualization._
    import com.here.platform.location.core.geospatial.GeoCoordinate._
    import play.api.libs.json._

    import scala.collection.immutable

    val segmentsAsFeatures = crossingSegments
      .flatMap {
        case VertexWithProperty(vertex, properties) =>
          properties.map {
            case RangeBasedProperty(start, end, (speedLimit, color)) =>
              val partialLine = LineStrings.cut(geometry(vertex), Seq((start, end))).head
              val shift =
                Visualization.shiftNorthWest(if (Vertices.directionOf(vertex) == Forward) 2 else -2) _
              Feature(partialLine.copy(points = partialLine.points.map(shift)),
                      Some(Stroke(color) + ("speedLimit" -> JsString(speedLimit.toString))))
          }
      }
      .to[immutable.Seq]
    val json = Json.toJson(FeatureCollection(segmentsAsFeatures))
    val path = FileNameHelper.exampleJsonFileFor(this).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println("\nA GeoJson representation of the result is available in " + path + "\n")
  }
}
