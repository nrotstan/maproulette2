// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session

import java.net.URLDecoder

import org.maproulette.utils.Utils
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}

/**
  * This holds the search parameters that are used to define task sets for retrieving random tasks
  * from the database
  *
  * TODO add spatial filters as well
  *
  * @author cuthbertm
  */
case class SearchLocation(left:Double, bottom:Double, right:Double, top:Double)

case class SearchParameters(projectIds:Option[List[Long]]=None,
                            projectSearch:Option[String]=None,
                            projectEnabled:Option[Boolean]=None,
                            challengeIds:Option[List[Long]]=None,
                            challengeTags:Option[List[String]]=None,
                            challengeTagConjunction:Option[Boolean]=None,
                            challengeSearch:Option[String]=None,
                            challengeEnabled:Option[Boolean]=None,
                            taskTags:Option[List[String]]=None,
                            taskTagConjunction:Option[Boolean]=None,
                            taskSearch:Option[String]=None,
                            taskStatus:Option[List[Int]]=None,
                            props:Option[Map[String, String]]=None,
                            priority:Option[Int]=None,
                            location:Option[SearchLocation]=None,
                            bounding:Option[SearchLocation]=None,
                            fuzzySearch:Option[Int]=None,
                            owner:Option[String]=None) {
  def getProjectIds : Option[List[Long]] = projectIds match {
    case Some(v) => Some(v.filter(_ != -1))
    case None => None
  }

  def getChallengeIds : Option[List[Long]] = challengeIds match {
    case Some(v) => Some(v.filter(_ != -1))
    case None => None
  }

  def getPriority : Option[Int] = priority match {
    case Some(v) if v == -1 => None
    case _ => priority
  }

  def hasTaskTags : Boolean = taskTags.getOrElse(List.empty).exists(tt => tt.nonEmpty)
  def hasChallengeTags : Boolean = challengeTags.getOrElse(List.empty).exists(ct => ct.nonEmpty)

  def enabledProject : Boolean = projectEnabled.getOrElse(true)
  def enabledChallenge : Boolean = challengeEnabled.getOrElse(true)
}

object SearchParameters {

  implicit val locationWrites = Json.writes[SearchLocation]
  implicit val locationReads = Json.reads[SearchLocation]
  implicit val paramsWrites = Json.writes[SearchParameters]
  implicit val paramsReads = Json.reads[SearchParameters]

  /**
    * Will attempt to convert the cookie to SearchParameters, if it fails it simply initializes an
    * empty SearchParameters
    *
    * @param value
    * @return
    */
  def convert(value:String) : SearchParameters = {
    try {
      Utils.omitEmpty(Json.parse(URLDecoder.decode(value, "UTF-8")).as[JsObject], false, false).as[SearchParameters]
    } catch {
      case e:Exception =>
        SearchParameters()
    }
  }

  /**
    * Retrieves the search cookie from the cookie list and creates a search parameter object
    * to send along with the request. It will also check the query string and if any parameters
    * are found it will override the values in the cookie.
    *
    * @param block The block of code to be executed after the cookie has been retrieved
    * @param request The request that the cookie came in on
    * @tparam T The response type from the block of code
    * @return The response from the block of code
    */
  def withSearch[T](block:SearchParameters => T)(implicit request:Request[AnyContent]) : T = {
    val params = request.cookies.get("search") match {
      case Some(c) => convert(c.value)
      case None => SearchParameters()
    }

    val projectIds = request.getQueryString("pid") match {
      case Some(q) => Utils.toLongList(q)
      case None => params.projectIds
    }

    val challengeIds = request.getQueryString("cid") match {
      case Some(q) => Utils.toLongList(q)
      case None => params.challengeIds
    }

    block(SearchParameters(
      //projectID
      projectIds,
      //projectSearch
      this.getStringParameter(request.getQueryString("ps"), params.projectSearch),
      //projectEnabled
      this.getBooleanParameter(request.getQueryString("pe"), params.projectEnabled),
      //challengeID
      challengeIds,
      //challengeTags
      request.getQueryString("ct") match {
        case Some(v) => Some(v.split(",").toList)
        case None => params.challengeTags
      },
      //challengeTagConjunction
      this.getBooleanParameter(request.getQueryString("ctc"), Some(params.challengeTagConjunction.getOrElse(false))),
      //challengeSearch
      this.getStringParameter(request.getQueryString("cs"), params.challengeSearch),
      //challengeEnabled
      this.getBooleanParameter(request.getQueryString("ce"), params.challengeEnabled),
      //taskTags
      request.getQueryString("tt") match {
        case Some(v) => Some(v.split(",").toList)
        case None => params.taskTags
      },
      //taskTagConjunction
      this.getBooleanParameter(request.getQueryString("ttc"), Some(params.taskTagConjunction.getOrElse(false))),
      //taskSearch
      this.getStringParameter(request.getQueryString("ts"), params.taskSearch),
      //taskStatus
      request.getQueryString("tStatus") match {
        case Some(v) => Utils.toIntList(v)
        case None => params.taskStatus
      },
      None,
      //taskPriority
      this.getIntParameter(request.getQueryString("tp"), params.priority),
      //taskBoundingBox for Challenge Location
      request.getQueryString("tbb") match {
        case Some(v) if v.nonEmpty =>
          v.split(",") match {
            case x if x.size == 4 => Some(SearchLocation(x(0).toDouble, x(1).toDouble, x(2).toDouble, x(3).toDouble))
            case _ => None
          }
        case _ => params.location
      },
      //taskBoundingBox for Challenge location
      request.getQueryString("bb") match {
        case Some(v) if v.nonEmpty =>
          v.split(",") match {
            case x if x.size == 4 => Some(SearchLocation(x(0).toDouble, x(1).toDouble, x(2).toDouble, x(3).toDouble))
            case _ => None
          }
        case _ => None
      },
      //FuzzySearch
      this.getIntParameter(request.getQueryString("fuzzy"), params.fuzzySearch),
      //Owner
      this.getStringParameter(request.getQueryString("o"), params.owner)
    ))
  }

  private def getBooleanParameter(value:Option[String], default:Option[Boolean]) : Option[Boolean] = value match {
    case Some(v) => Some(v.toBoolean)
    case None => default
  }

  private def getLongParameter(value:Option[String], default:Option[Long]) : Option[Long] = value match {
    case Some(v) => Some(v.toLong)
    case None => default
  }

  private def getIntParameter(value:Option[String], default:Option[Int]) : Option[Int] = value match {
    case Some(v) => Some(v.toInt)
    case None => default
  }

  private def getStringParameter(value:Option[String], default:Option[String]) : Option[String] = value match {
    case Some(v) => Some(v)
    case None => default
  }
}
