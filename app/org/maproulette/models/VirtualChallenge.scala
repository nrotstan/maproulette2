package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.actions.{ItemType, VirtualChallengeType}
import org.maproulette.session.SearchParameters
import play.api.libs.json.{DefaultWrites, Json, Reads, Writes}

/**
  * @author mcuthbert
  */
case class VirtualChallenge(override val id:Long,
                            override val name:String,
                            override val created:DateTime,
                            override val modified:DateTime,
                            override val description:Option[String]=None,
                            ownerId:Long,
                            searchParameters:SearchParameters,
                            expiry:DateTime) extends BaseObject[Long] with DefaultWrites {

  override val itemType: ItemType = VirtualChallengeType()

  def isExpired = DateTime.now().isAfter(expiry)
}

object VirtualChallenge {
  implicit val virtualChallengeWrites:Writes[VirtualChallenge] = Json.writes[VirtualChallenge]
  implicit val virtualChallengeReads:Reads[VirtualChallenge] = Json.reads[VirtualChallenge]
}
