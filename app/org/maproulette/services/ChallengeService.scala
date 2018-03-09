// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.exception.InvalidException
import org.maproulette.models.{Challenge, Task}
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import org.maproulette.session.User
import play.api.Logger
import play.api.db.Database
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
@Singleton
class ChallengeService @Inject() (challengeDAL: ChallengeDAL, taskDAL: TaskDAL,
                                  config:Config, ws:WSClient, db:Database) extends DefaultReads {
  import scala.concurrent.ExecutionContext.Implicits.global

  def rebuildChallengeTasks(user:User, challenge:Challenge) : Boolean = this.buildChallengeTasks(user, challenge)

  def buildChallengeTasks(user:User, challenge:Challenge, json:Option[String]=None) : Boolean = {
    if (!challenge.creation.overpassQL.getOrElse("").isEmpty) {
      this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_BUILDING), user)(challenge.id)
      Future {
        Logger.debug("Creating tasks for overpass query: " + challenge.creation.overpassQL.get)
        this.buildOverpassQLTasks(challenge, user)
      }
      true
    } else {
      val usingLocalJson = json match {
        case Some(value) if StringUtils.isNotEmpty(value) =>
          this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_BUILDING), user)(challenge.id)
          Future {
            Logger.debug("Creating tasks from local GeoJSON file")
            this.createTasksFromFeatures(user, challenge, Json.parse(value))
          }
          true
        case _ => false
      }
      if (!usingLocalJson) {
        // lastly try remote
        challenge.creation.remoteGeoJson match {
          case Some(url) if StringUtils.isNotEmpty(url) =>
            this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_BUILDING), user)(challenge.id)
            this.ws.url(url).withRequestTimeout(this.config.getOSMQLProvider.requestTimeout).get() onComplete {
              case Success(resp) =>
                Logger.debug("Creating tasks from remote GeoJSON file")
                this.createTasksFromFeatures(user, challenge, Json.parse(resp.body))
              case Failure(f) =>
                this.challengeDAL.update(Json.obj("overpassStatus" -> Challenge.STATUS_FAILED), user)(challenge.id)
            }
            true
          case _ => false
        }
      } else {
        false
      }
    }
  }

  private def createTasksFromFeatures(user:User, parent:Challenge, jsonData:JsValue) = {
    val featureList = (jsonData \ "features").as[List[JsValue]]
    try {
      featureList.map { value =>
        val name = (value \ "id").asOpt[String] match {
          case Some(n) => n
          case None => (value \ "name").asOpt[String] match {
            case Some(na) => na
            case None =>
              // if we still don't find anything, create a UUID for it.
              // The caveat to this is that if you upload the same file again, it will create
              // duplicate tasks
              UUID.randomUUID().toString
          }
        }
        this.createNewTask(user, name, parent, (value \ "geometry").as[JsObject], getProperties(value, "properties"))
      }
      this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_COMPLETE), user)(parent.id)
      Logger.debug(s"${featureList.size} tasks created from json file.")
    } catch {
      case e:Exception =>
        this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_FAILED), user)(parent.id)
        Logger.error(s"${featureList.size} tasks failed to be created from json file.", e)
    }
  }

  /**
    * Based on the supplied overpass query this will generate the tasks for the challenge
    *
    * @param challenge The challenge to create the tasks under
    * @param user The user executing the query
    */
  private def buildOverpassQLTasks(challenge:Challenge, user:User) = {
    challenge.creation.overpassQL match {
      case Some(ql) if StringUtils.isNotEmpty(ql) =>
        // run the query and then create the tasks
        val osmQLProvider = config.getOSMQLProvider
        val timeoutPattern = "\\[timeout:([\\d]*)\\]".r
        val timeout = timeoutPattern.findAllIn(ql).matchData.toList.headOption match {
          case Some(m) => Duration(m.group(1).toInt, "seconds")
          case None => osmQLProvider.requestTimeout
        }

        val jsonFuture = this.ws.url(osmQLProvider.providerURL).withRequestTimeout(timeout).post(parseQuery(ql))
        jsonFuture onComplete {
          case Success(result) =>
            if (result.status == Status.OK) {
              this.db.withTransaction { implicit c =>
                var partial = false
                val payload = result.json
                // parse the results
                val elements = (payload \ "elements").as[List[JsValue]]
                elements.foreach {
                  element =>
                    try {
                      val geometry = (element \ "type").asOpt[String] match {
                        case Some("way") =>
                          val points = (element \ "geometry").as[List[JsValue]].map {
                            geom => List((geom \ "lon").as[Double], (geom \ "lat").as[Double])
                          }
                          Some(Json.obj("type" -> "LineString", "coordinates" -> points))
                        case Some("node") =>
                          Some(Json.obj(
                            "type" -> "Point",
                            "coordinates" -> List((element \ "lon").as[Double], (element \ "lat").as[Double])
                          ))
                        case _ => None
                      }

                      geometry match {
                        case Some(geom) =>
                          this.createNewTask(user, (element \ "id").as[Long] + "", challenge, geom, this.getProperties(element, "tags"))
                        case None => None
                      }
                    } catch {
                      case e:Exception =>
                        partial = true
                        Logger.error(e.getMessage, e)
                    }
                }
                partial match {
                  case true =>
                    this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_PARTIALLY_LOADED), user)(challenge.id)
                  case false =>
                    this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_COMPLETE), user)(challenge.id)
                }
              }
            } else {
              this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_FAILED), user)(challenge.id)
              throw new InvalidException(s"Bad Request: ${result.body}")
            }
          case Failure(f) =>
            this.challengeDAL.update(Json.obj("status" -> Challenge.STATUS_FAILED), user)(challenge.id)
            throw f
        }
      case None => // just ignore, we don't have to do anything if it wasn't set
    }
  }

  private def createNewTask(user:User, name:String, parent:Challenge, geometry:JsObject,
                            properties:JsValue) : Boolean = {
    val newTask = Task(-1, name, DateTime.now(), DateTime.now(),
      parent.id,
      Some(""),
      None,
      Json.obj(
        "type" -> "FeatureCollection",
        "features" -> Json.arr(Json.obj(
          "id" -> name,
          "geometry" -> geometry,
          "properties" -> properties
        ))
      ).toString
    )

    try {
      this.taskDAL.mergeUpdate(newTask, user)(newTask.id)
      true
    } catch {
      // this task could fail on unique key violation, we need to ignore them
      case e:Exception =>
        Logger.error(e.getMessage)
        false
    }
  }

  /**
    * parse the query, replace various extended overpass query parameters see http://wiki.openstreetmap.org/wiki/Overpass_turbo/Extended_Overpass_Queries
    * Currently do not support {{bbox}} or {{center}}
    *
    * @param query The query to parse
    * @return
    */
  private def parseQuery(query:String) : String = {
    val osmQLProvider = config.getOSMQLProvider
    // User can set their own custom timeout if the want
    if (query.indexOf("[out:json]") == 0) {
      query
    } else if (query.indexOf("[timeout:") == 0) {
      s"[out:json]$query"
    } else {
      s"[out:json][timeout:${osmQLProvider.requestTimeout.toSeconds}];$query"
    }
    // execute regex matching against {{data:string}}, {{geocodeId:name}}, {{geocodeArea:name}}, {{geocodeBbox:name}}, {{geocodeCoords:name}}
  }

  private def getProperties(value:JsValue, key:String) : JsValue = {
    (value \ key).asOpt[JsObject] match {
      case Some(JsObject(p)) =>
        val idMap = (value \ "id").toOption match {
          case Some(idValue) => p + ("osmid" -> idValue)
          case None => p
        }
        val updatedMap = idMap.map {
          kv =>
            try {
              val strValue = kv._2 match {
                case v: JsNumber => v.toString
                case v: JsArray => v.as[Seq[String]].mkString(",")
                case v => v.as[String]
              }
              kv._1 -> strValue
            } catch {
              // if we can't convert it into a string, then just use the toString method and hope we get something sensible
              // other option could be just to ignore it.
              case e:Throwable =>
                kv._1 -> kv._2.toString
            }
        }.toMap
        Json.toJson(updatedMap)
      case _ => Json.obj()
    }
  }
}
