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
  val companies = Tables.Companies
  val commodities = Tables.Commodities

  import dbConfig.driver.api._


  def getInfo(contId: Int): Future[Option[(Int, String, String, String)]] = {
    val query = for {
      (((cont, comm), cons), prod) <- contracts join
        commodities on (_.commodityId === _.commodityId) join
        companies on (_._1.consumerId === _.companyId) join
        companies on (_._1._1.producerId === _.companyId)
    } yield (cont.contractNumber, comm.name, cons.companyName, prod.companyName)

    db.run(query.result.headOption)
  }
}
