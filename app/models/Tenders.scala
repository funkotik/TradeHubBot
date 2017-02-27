package models

import javax.inject.Inject
import java.sql.{Date => DateSQL}

import models.Tables.TendersRow
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, ISODateTimeFormat}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsBoolean, JsNumber, JsString}
import play.api.libs.ws.WSClient
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/**
  * Created by v-yaroslavskyi on 2/15/17.
  */
class Tenders @Inject()(dbConfigProvider: DatabaseConfigProvider,
                        configuration: play.api.Configuration,
                        ws: WSClient)
                       (implicit exc: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  val db: JdbcBackend#DatabaseDef = dbConfig.db
  val tenders = Tables.Tenders

  import dbConfig.driver.api._

  val rialtoUrl: String = configuration.getString("rialto.url").get
  val prozorroUrl: String = configuration.getString("prozorro.url").get

  def refreshTenders(commercial: Boolean) = {
    getLastUpdateStamp(commercial).map(
      _.map { x =>
        getAllTenders(commercial, Some(new DateTime(x)))
      }
    )

  }


  def getAllTenders(commercial: Boolean, startDate: Option[DateTime] = None): Future[Int] = {

    def getPage(url: String): Future[Option[(String, Seq[String])]] = {
      val respFut = ws.url(url).get
      respFut.map { resp =>
        val js = resp.json
        for {
          nextPageLink <- Try((js \ "next_page" \ "uri").get.as[JsString].value).toOption
          tendersIds <- Try((js \ "data" \\ "id").map(_.as[JsString].value)).toOption
        } yield {
          (nextPageLink, tendersIds)
        }
      }
    }

    def getTender(url: String): Future[Option[TendersRow]] = {
      val respFut = ws.url(url).get
      val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")
      respFut.map { resp =>
        val js = resp.json
        for {
          startDate <- Try {
            val dateStr = (js \ "data" \ "tenderPeriod" \ "startDate").get.as[JsString].value
            formatter.parseDateTime(dateStr)
          }
          endDate <- Try {
            val dateStr = (js \ "data" \ "tenderPeriod" \ "endDate").get.as[JsString].value
            formatter.parseDateTime(dateStr)
          }
          dateModified <- Try {
            val dateStr = (js \ "data" \ "dateModified").get.as[JsString].value
            formatter.parseDateTime(dateStr)
          }

          //          nBids <- Try((js \ "data" \ "numberOfBids").get.as[JsNumber].value.toInt)
          amount <- Try((js \ "data" \ "value" \ "amount").get.as[JsNumber].value.toDouble)
          currency <- Try((js \ "data" \ "value" \ "currency").get.as[JsString].value)
          taxIncluded <- Try((js \ "data" \ "value" \ "valueAddedTaxIncluded").get.as[JsBoolean].value)
          authorCompany <- Try((js \ "data" \ "procuringEntity" \ "name").get.as[JsString].value)
          telephone <- Try((js \ "data" \ "procuringEntity" \ "contactPoint" \ "telephone").get.as[JsString].value)
          title <- Try((js \ "data" \ "title").get.as[JsString].value)
          zpuId <- Try((js \ "data" \ "id").get.as[JsString].value)
          link <- Try((js \ "data" \ "tenderID").get.as[JsString].value)
          status <- Try((js \ "data" \ "status").get.as[JsString].value)
        } yield {
          val description = Try((js \ "data" \ "description").get.as[JsString].value).toOption
          val lotsText = Try((js \ "data" \ "items" \\ "description").map(_.as[JsString].value).mkString(" ")).toOption
          println("got tender")
          TendersRow(0, new DateSQL(startDate.toDate.getTime), new DateSQL(endDate.toDate.getTime), 0, amount, currency, taxIncluded,
            title, description, zpuId, link, lotsText, authorCompany, telephone, status, isCommercial = commercial, stampModified = dateModified.getMillis)
        }
      }.map(_.toOption)
    }

    def parse(url: String, tendersIds: Seq[String]): Future[Seq[String]] = {
      println(url, tendersIds.length)
      getPage(url).flatMap {
        case Some((nextPageLink, idsSeq)) if nextPageLink != url => parse(nextPageLink, tendersIds ++ idsSeq)
        case _ => Future successful tendersIds
      }
    }

    val url = if (commercial) rialtoUrl else prozorroUrl
    val ids = parse(url + "/tenders?offset=" + startDate.map(_.toString("yyyy-MM-dd'T'HH'%3A'mm'%3A'ss.SSSSSS'%2B03%3A00'")).getOrElse(""), Seq())

    ids.map(
      _.foldLeft(Seq[TendersRow]()) {
        case (s, id) =>
          val res = Await.result(getTender(s"$url/tenders/$id"), Duration.Inf)
          res match {
            case Some(r) =>
              if (s.length >= 100) {
                bulkInsert(s)
                Seq(r)
              }
              else
                s :+ r

            case _ => s
          }
      }
    ).flatMap(bulkInsert)
  }

//  def upsert = {
//    db.run(tenders.insertOrUpdate())
//  }

  def find(query: Seq[String], commercial: Option[Boolean] = None): Future[Seq[TendersRow]] = {
    def cond(x: Tables.Tenders) =
      query.map { q =>
        x.authorCompany.toLowerCase.like(s"%${q.toLowerCase}%") ||
          x.lotsText.toLowerCase.like(s"%${q.toLowerCase}%") ||
          x.title.toLowerCase.like(s"%${q.toLowerCase}%")
      }.reduce(_ || _)


    db.run(tenders.filter(x =>
      cond(x) &&
        (x.status === "active.tendering" || x.status === "active.enquiries") &&
        ((x.isCommercial === commercial) || commercial.isEmpty)
    ).result)
  }

  def bulkInsert(tdrs: Seq[TendersRow]): Future[Int] = {
    println(s"inserting bulk ${tdrs.length} elements")
    db.run(tenders returning tenders.map(_.id) ++= tdrs).map(_.length)
  }

  def getLastUpdateStamp(commercial: Boolean): Future[Option[Long]] = {
    db.run(tenders.filter(_.isCommercial === commercial).sortBy(_.stampModified).map(_.stampModified).result.headOption)
  }

}
