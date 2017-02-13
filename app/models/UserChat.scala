package models

import javax.inject.Inject

import models.Tables.UsersChatsRow
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend
import telegram.Contact


import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by v-yaroslavskyi on 2/12/17.
  */

class UserChat @Inject()(dbConfigProvider: DatabaseConfigProvider)
                        (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val userChats = Tables.UsersChats
  val contracts = Tables.Contracts
  val commodities = Tables.Commodities
  val companies = Tables.Companies

  import dbConfig.driver.api._

  def get(tel: String): Future[Option[UsersChatsRow]] =
    db.run(userChats.filter(_.telephone.like(s"%$tel%")).result.headOption)

  def get(partnerId: Int): Future[Option[UsersChatsRow]] =
    db.run(userChats.filter(_.contragentId === partnerId).result.headOption)

  def get(chatId: Long): Future[Option[UsersChatsRow]] = {
    db.run(userChats.filter(_.chatId === chatId.toInt).result.headOption)
  }

  def insert(user: UsersChatsRow): Future[Int] =
    db.run(userChats returning userChats.map(_.id) += user)

  def del(id: Int): Future[Int] =
    db.run(userChats.filter(_.id === id).delete)

  def getUserCommodities(userId: Long, isSell: Boolean): Future[Seq[(Int, String)]] = {
    val query = for {
      (comm, _) <- {
        if (isSell)
          userChats join
            contracts on (_.contragentId === _.producerId) join
            commodities on (_._2.commodityId === _.commodityId) groupBy (_._2)
        else
          userChats join
            contracts on (_.contragentId === _.consumerId) join
            commodities on (_._2.commodityId === _.commodityId) groupBy (_._2)
      }
    } yield (comm.commodityId, comm.name)

    db.run(query.result)
  }

  def getPartners(userId: Long, commodityId: Int, isSell: Boolean): Future[Seq[(Int, String, Int, Int)]] = {
    val query = for {
      ((usr, cont), com) <- {
        if (isSell)
          userChats join
            contracts.filter(_.commodityId === commodityId) on (_.contragentId === _.producerId) join
            companies on (_._2.consumerId === _.companyId)

        else
          userChats join
            contracts.filter(_.commodityId === commodityId) on (_.contragentId === _.consumerId) join
            companies on (_._2.producerId === _.companyId)
      }
    } yield (com.companyId, com.companyName, cont.contractNumber, cont.contractId)

    db.run(query.result)
  }
}