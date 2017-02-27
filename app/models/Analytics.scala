package models

import java.util.{Calendar, Date, GregorianCalendar}
import javax.inject.Inject
import javax.xml.datatype
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

//import co.theasi.plotly._
//import com.ning.http.client.Realm.{AuthScheme, RealmBuilder}
//import dispatch.{Future, as, url}
//import generated.ExpectedPriceResults
//import org.joda.time.{DateTime, DateTimeZone, ReadableDuration}
//
import scala.concurrent.ExecutionContext
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent._
import scala.util.Random

/**
  * Created by v-yaroslavskyi on 2/19/17.
  */

class Service extends generated.PricecurveBindings with
  scalaxb.Soap11ClientsAsync with
  scalaxb.DispatchHttpClientsAsync {

}


class Analytics @Inject()(ws: WSClient)(implicit exc: ExecutionContext) {


  private val service = (new Service).service


//  def getAnalytics: Future[ExpectedPriceResults] = {
//
//    // Generate uniformly distributed x
//    val dates = (1 to 11).map(x => new DateTime(2016, 12, 2, 0, 0).withTimeAtStartOfDay().plusWeeks(x).toString())
//
//    val ma = List(16119.1923, 16377.4246, 16640.0274, 16907.0813, 17178.6685,  17454.873, 17735.7805, 18021.4785, 18312.0561, 18607.6047, 18908.2172)
//    val av = List(15039.1002, 15264.6867, 15493.657,  15726.0618, 15961.9527,  16201.382, 16444.4028, 16691.0688, 16941.4348, 17195.5564, 17453.4897)
//    val mi = List(11798.8238, 11926.4729, 12054.5457, 12183.0034, 12311.8054, 12440.909, 12570.2694, 12699.8398, 12829.5709, 12959.4113, 13089.3072)
//
//    val p = Plot()
//      .withScatter(dates, av,
//        ScatterOptions(
//          Some("Ожидаемая цена"),
//          Seq(ScatterMode.Line),
//          None,
//          MarkerOptions(Some(2), Some(Color.rgb(0, 0, 255)), Some("30"), None, None)
//        )
//      ).withScatter(dates, ma).withScatter(dates, mi)
//
//
//    val file = draw(p, "basic-scatter", writer.FileOptions(overwrite=true))
//
//
//    val c = new GregorianCalendar()
//    val d = new Date()
//    d.setTime(new DateTime().withTimeAtStartOfDay().getMillis)
//    c.setTime(d)
//    c.add(Calendar.DATE, -3)
//    val dateStart = DatatypeFactory.newInstance().newXMLGregorianCalendar(c)
//    c.add(Calendar.DATE, 120)
//    val dateEnd = DatatypeFactory.newInstance().newXMLGregorianCalendar(c)
//    println(dateEnd, dateStart)
//    service.getExpectedPriceInRange("WS.EXW.UAH.SPOT", 0.85, dateStart, dateEnd, "1w")
//  }

}
