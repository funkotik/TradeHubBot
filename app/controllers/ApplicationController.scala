package controllers

import java.io.File
import javax.inject.{Inject, Singleton}

import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient, Response}
import com.ning.http.client.multipart.StringPart
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import com.ning.http.client.multipart.FilePart
import scala.concurrent.Promise
import play.api.libs.ws.ning.NingWSResponse
import play.api.mvc.{Action, Controller, MultipartFormData}

import scala.concurrent.{ExecutionContext, Future}

//@Singleton
class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration)
                                     (implicit val exc: ExecutionContext)
  extends Controller {

  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus = setWebhook.map{x =>
    println(x.body)
    ws.url(url + "/getWebhookInfo").get().map(x => println(x.body))
  }

  def index = Action {request =>
    println(request)
    Ok("lol")
  }

  def log(any: String) = Action { request =>
    println(any, request)
    Ok("kek")
  }

  def setWebhook = {

    val bodyParts = List(
      new StringPart("url", "https://52.174.38.160", "UTF-8"),
      new FilePart("certificate", new File("/home/vova/TradeHubBot/public/certificates/nginx.crt"))
    )
    val client = ws.underlying.asInstanceOf[AsyncHttpClient]

    val builder = client.preparePost(url + "/setWebhook" +
      "")

    builder.setHeader("Content-Type", "multipart/form-data")
    bodyParts.foreach(builder.addBodyPart)

    var result = Promise[NingWSResponse]()

    client.executeRequest(builder.build(), new AsyncCompletionHandler[Response]() {
      override def onCompleted(response: Response) = {
        println(response.getResponseBody)
        result.success(NingWSResponse(response))
        response
      }

      override def onThrowable(t: Throwable) = {
        result.failure(t)
      }
    })

    result.future
  }
}
