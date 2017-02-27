package models

import javax.inject.Inject

import models.Tables._
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
  val contracts = Tables.Contracts
  val companies = Tables.Companies
  val userChats = Tables.UsersChats
  val commodities = Tables.Commodities

  import dbConfig.driver.api._

  def insert(bid: BidsRow): Future[Int] =
    db.run(bids returning bids.map(_.id) += bid)

  def getWithChats(bidId: Int): Future[Option[(BidsRow, ContractsRow, CommoditiesRow, UsersChatsRow, UsersChatsRow, CompaniesRow, CompaniesRow)]] = {
    val query = for {
      ((((((bid, cont), prod), cons), commodity), prodCompany), consCompany) <- bids join
        contracts on (_.contractId === _.contractId) join
        userChats on (_._2.producerId === _.contragentId) join
        userChats on (_._1._2.consumerId === _.contragentId) join
        commodities on (_._1._1._2.commodityId === _.commodityId) join
        companies on (_._1._1._1._2.producerId === _.companyId) join
        companies on (_._1._1._1._1._2.consumerId === _.companyId)

      if bid.id === bidId
    } yield {
      (bid, cont, commodity, prod, cons, prodCompany, consCompany)
    }
    db.run(query.result.headOption)
  }

  def updateStatus(bidId: Int, status: Boolean, prod: Boolean): Future[Int] = {
    db.run(bids.filter(_.id === bidId).map(x => if(prod) x.producerConfirmed else x.consumerConfirmed).update(Some(status)))
  }
}
