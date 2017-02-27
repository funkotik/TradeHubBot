package models

import javax.inject.Inject

import models.Tables.MonitoringNewsRow
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by v-yaroslavskyi on 2/12/17.
  */
class MonitoringNews @Inject()(dbConfigProvider: DatabaseConfigProvider)
                              (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val monitoringNews = Tables.MonitoringNews

  import dbConfig.driver.api._

  def add_subscription(chatId: Long, query: String): Future[Int] =
    db.run(monitoringNews returning monitoringNews.map(_.id) += MonitoringNewsRow(0, chatId.toInt, query, active=true))


  def get(chatId:Long): Future[Seq[MonitoringNewsRow]] = {
    db.run(monitoringNews.filter(_.chatId === chatId.toInt).result)
  }

  def del(id: Int): Future[Int] = {
    db.run(monitoringNews.filter(_.id === id).delete)
  }
}
