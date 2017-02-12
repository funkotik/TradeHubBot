package models

import javax.inject.Inject

import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend
import Tables._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by v-yaroslavskyi on 2/12/17.
  */
class Contract @Inject()(dbConfigProvider: DatabaseConfigProvider)
                        (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val contracts = Tables.Contracts

  import dbConfig.driver.api._


  def get(contId: Int): Future[Option[ContractsRow]] =
    db.run(contracts.filter(_.contractId === contId).result.headOption)
}
