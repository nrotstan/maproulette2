package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.exception.{NotFoundException, StatusMessage}
import org.maproulette.models.{Challenge, Task}
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SessionManager, User, UserSettings}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
class UserController @Inject()(userDAL: UserDAL, sessionManager: SessionManager) extends Controller with DefaultWrites {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val userReadWrite = User.UserFormat
  implicit val challengeWrites = Challenge.writes.challengeWrites
  implicit val taskWrites = Task.TaskFormat

  def getUser(userId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (userId == user.id || userId == user.osmProfile.id) {
        Ok(Json.toJson(user))
      } else if (user.isSuperUser) {
        this.userDAL.retrieveByOSMID(userId, user) match {
          case Some(u) => Ok(Json.toJson(u))
          case None => this.userDAL.retrieveById(userId) match {
            case Some(u) => Ok(Json.toJson(u))
            case None => throw new NotFoundException(s"No user found with id '$userId'")
          }
        }
      } else {
        throw new IllegalAccessException("Only super users have access to other user account information")
      }
    }
  }

  def getUserByOSMUsername(username: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (user.name == username) {
        Ok(Json.toJson(user))
      } else {
        // we don't need to check access here as the API only allows super users to make the call,
        // so if not a super user, the correct IllegalAccessException will be thrown
        this.userDAL.retrieveByOSMUsername(username, user) match {
          case Some(u) => Ok(Json.toJson(u))
          case None => throw new NotFoundException(s"No user found with OSM username '$username'")
        }
      }
    }
  }

  def updateUser(id: Long): Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      // if the request body contains the API Key for update, let's update that separately
      (request.body \ "apiKey").asOpt[String] match {
        case Some(key) => userDAL.update(Json.parse(s"""{"apiKey":"$key"}"""), user)(id)
        case None => //just ignore, we don't have to do anything if it isn't there
      }
      implicit val settingsRead = User.settingsReads
      userDAL.managedUpdate(request.body.as[UserSettings], (request.body \ "properties").toOption, user)(id) match {
        case Some(u) => Ok(Json.toJson(u))
        case None => throw new NotFoundException(s"No user found to update with id '$id'")
      }
    }
  }

  /**
    * Action to refresh the user's OSM profile
    *
    * @return Ok Status with no content
    */
  def refreshProfile(osmUserId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedFutureRequest { implicit user =>
      val p = Promise[Result]
      this.userDAL.retrieveByOSMID(osmUserId, user) match {
        case Some(u) =>
          sessionManager.refreshProfile(u.osmProfile.requestToken, user) onComplete {
            case Success(result) => p success Ok
            case Failure(f) => p failure f
          }
        case None => p failure new NotFoundException(s"Failed to find any user with OSM User id [$osmUserId]")
      }
      p.future
    }
  }

  def getSavedChallenges(userId: Long, limit:Int, offset:Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.userDAL.getSavedChallenges(userId, user, limit, offset)))
    }
  }

  def saveChallenge(userId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.saveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId saved for user $userId"))))
    }
  }

  def unsaveChallenge(userId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.unsaveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId unsaved from user $userId"))))
    }
  }

  def getSavedTasks(userId: Long, challengeIds:String, limit:Int, offset:Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val ids = challengeIds.split(",").filter(_.nonEmpty) match {
        case v if v.nonEmpty => v.map(_.toLong).toSeq
        case _ => Seq.empty[Long]
      }
      Ok(Json.toJson(this.userDAL.getSavedTasks(userId, user, ids, limit, offset)))
    }
  }

  def saveTask(userId: Long, taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.saveTask(userId, taskId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Task $taskId saved for user $userId"))))
    }
  }

  def unsaveTask(userId: Long, taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.unsaveTask(userId, taskId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Task $taskId unsaved from user $userId"))))
    }
  }
}
