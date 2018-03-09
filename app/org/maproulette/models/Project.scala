// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.actions.{ItemType, ProjectType}
import play.api.data._
import play.api.data.Forms._
import org.maproulette.session.{Group, User}
import play.api.libs.json.{Json, Reads, Writes}

/**
  * The project object is the root object of hierarchy, it is built to allow users to have personal
  * domains where they can create their own challenges and have a permissions model that allows
  * users to have and give control over what happens within that domain.
  *
  * @author cuthbertm
  */
case class Project(override val id: Long,
                   owner: Long,
                   override val name: String,
                   override val created:DateTime,
                   override val modified:DateTime,
                   override val description: Option[String]=None,
                   groups:List[Group]=List.empty,
                   enabled:Boolean=false,
                   displayName: Option[String]=None,
                   deleted:Boolean=false) extends BaseObject[Long] {

  override val itemType: ItemType = ProjectType()
}

object Project {
  implicit val groupWrites: Writes[Group] = Json.writes[Group]
  implicit val groupReads: Reads[Group] = Json.reads[Group]
  implicit val projectWrites: Writes[Project] = Json.writes[Project]
  implicit val projectReads: Reads[Project] = Json.reads[Project]

  val KEY_GROUPS = "groups"

  val projectForm = Form(
    mapping(
      "id" -> default(longNumber,-1L),
      "owner" -> default(longNumber, User.DEFAULT_SUPER_USER_ID.toLong),
      "name" -> nonEmptyText,
      "created" -> default(jodaDate, DateTime.now()),
      "modified" -> default(jodaDate, DateTime.now()),
      "description" -> optional(text),
      KEY_GROUPS -> list(
        mapping(
          "id" -> longNumber,
          "name" -> nonEmptyText,
          "projectId" -> longNumber,
          "groupType" -> number(min = 1, max = 1),
          "created" -> default(jodaDate, DateTime.now()),
          "modified" -> default(jodaDate, DateTime.now())
        )(Group.apply)(Group.unapply)
      ),
      "enabled" -> boolean,
      "displayName" -> optional(text),
      "deleted" -> default(boolean, false)
    )(Project.apply)(Project.unapply)
  )

  def emptyProject : Project = Project(-1, User.DEFAULT_SUPER_USER_ID, "", DateTime.now(), DateTime.now())
}
