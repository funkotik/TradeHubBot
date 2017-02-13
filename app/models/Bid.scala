package models

import javax.inject.Inject

import models.Tables.BidsRow
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by v-yaroslavskyi on 2/12/17.
  */
class Bid @Inject()(dbConfigProvider: DatabaseConfigProvider)
                   (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val bids = Tables.Bids

  import dbConfig.driver.api._

  def insert(bid: BidsRow): Future[Int] =
    db.run(bids returning bids.map(_.id) += bid)


}
