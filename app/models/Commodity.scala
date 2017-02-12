package models

import javax.inject.Inject

import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext

/**
  * Created by v-yaroslavskyi on 2/12/17.
  */
class Commodity @Inject()(dbConfigProvider: DatabaseConfigProvider)
                         (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val commodities = Tables.Commodities


}
