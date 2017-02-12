package controllers

import java.io.File
import javax.inject.{Inject, Singleton}

import telegram._
import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient, Response}
import com.ning.http.client.multipart.StringPart
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import com.ning.http.client.multipart.FilePart
import org.json4s

import scala.concurrent.Promise
import play.api.libs.ws.ning.NingWSResponse
import play.api.mvc.{Action, Controller, MultipartFormData}

import scala.concurrent.{ExecutionContext, Future}
import org.json4s._
import org.json4s.native.{JsonMethods, Serialization}
import org.json4s.native.JsonMethods.{parse => parse4s, render => render4s, _}
import telegram.methods.{ChatAction, ParseMode, SendMessage}
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.Json4sModule



//@Singleton
class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration)
                                     (implicit val exc: ExecutionContext)
  extends Controller {


  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus: Future[Unit] = setWebhook.map{ x =>
    println(x.body)
  }

  implicit val formats = Serialization.formats(NoTypeHints) +
    new EnumNameSerializer(ChatAction) +
    new EnumNameSerializer(ParseMode)

  def toJson[T](t: T): String = compact(render4s(Extraction.decompose(t).underscoreKeys))

  def toAnswerJson[T](t: T, method: String): JsValue =
    Json.parse(
      compact(
      render4s(new JObject(Extraction.decompose(t).asInstanceOf[JObject].obj ++
        List("method" -> JString(method))).underscoreKeys)
    )
    )

  def fromJson[T: Manifest](json: String): T = parse4s(json).camelizeKeys.extract[T]


  def index = Action {request =>
    Ok("lol")
  }

  def inbox = Action { request =>
    val js = request.body.asJson.get
    val update = fromJson[Update](js.toString)
    val response = update.message map {msg =>
      val command = getCommand(msg)
      println(command)
      command match {

        case Some("/start") => start(msg)
        case _ => SendMessage(Left(msg.chat.id), "Пока что я слишком слаб чтобы понять это")
      }

    }



    response.map(x => Ok(toAnswerJson(x, x.methodName))).getOrElse(Ok())
  }

  def start(msg: Message) = {
    SendMessage(Left(msg.chat.id),
      "Я брокер-бот, мониторю торговые площадки, делаю прогнозы рынков," +
      " помогаю заключить контракт, организовываю торговые коммуникации")
  }

  def getCommand(msg: Message): Option[String] = {
    msg.entities.getOrElse(Seq()).find { me =>
      me.`type` == "bot_command" && me.offset == 0
    }.flatMap{me =>
      msg.text.map(_.slice(0, me.length))
    }
  }

  def setWebhook: Future[NingWSResponse] = {

    val bodyParts = List(
      new StringPart("url", "https://52.174.38.160/webhook", "UTF-8"),
      new FilePart("certificate", new File(s"${conf.getString("filePrefix").get}public/certificates/nginx.crt"))
    )
    val client = ws.underlying.asInstanceOf[AsyncHttpClient]

    val builder = client.preparePost(url + "/setWebhook" +
      "")

    builder.setHeader("Content-Type", "multipart/form-data")
    bodyParts.foreach(builder.addBodyPart)

    var result = Promise[NingWSResponse]()

    client.executeRequest(builder.build(), new AsyncCompletionHandler[Response]() {
      override def onCompleted(response: Response) = {
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
