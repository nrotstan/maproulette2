// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json, Reads, Writes}

/**
  * @author cuthbertm
  */
case class Point(lat:Double, lng:Double)

/**
  * This is the clustered point that will be displayed on the map. The popup will contain the title
  * of object with a blurb or description of said object. If the object is a challenge then below
  * that will be a start button so you can jump into editing tasks in the challenge
  *
  * @param id The id of the object for this clustered point
  * @param owner The osm id of the owner of the object
  * @param ownerName The name of the owner
  * @param title The title of the object (or name)
  * @param parentId The id of the parent, Challenge if Task, and Project if Challenge
  * @param parentName The name of the parent
  * @param point The latitude and longitude of the point
  * @param blurb A short descriptive text for the object
  * @param modified The last time this set of points was modified
  * @param difficulty The difficulty level of this ClusteredPoint (if a challenge)
  * @param type The type of this ClusteredPoint
  * @param status The status of the task, only used for task points, ie. not challenge points
  */
case class ClusteredPoint(id:Long, owner:Long, ownerName:String, title:String, parentId:Long, parentName:String,
                          point:Point, bounding:JsValue, blurb:String, modified:DateTime, difficulty:Int,
                          `type`:Int, status:Int, priority:Int)

object ClusteredPoint {
  implicit val pointWrites: Writes[Point] = Json.writes[Point]
  implicit val pointReads: Reads[Point] = Json.reads[Point]
  implicit val clusteredPointWrites: Writes[ClusteredPoint] = Json.writes[ClusteredPoint]
  implicit val clusteredPointReads: Reads[ClusteredPoint] = Json.reads[ClusteredPoint]
}
