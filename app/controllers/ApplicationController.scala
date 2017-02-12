package controllers

import java.io.File
import javax.inject.Inject

import models._
import telegram._
import org.asynchttpclient.{AsyncCompletionHandler, AsyncHttpClient, Response}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import org.asynchttpclient.request.body.multipart.{FilePart, StringPart}

import scala.concurrent.Promise
import play.api.mvc.{Action, Controller}

import scala.concurrent.{ExecutionContext, Future}
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.JsonMethods.{parse => parse4s, render => render4s, _}
import telegram.methods.{ChatAction, ParseMode, SendMessage}
import org.json4s.ext.EnumNameSerializer
import play.api.libs.ws.ahc.AhcWSResponse


class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration,
                                      company: Company, commodity: Commodity,
                                      bid: Bid, contract: Contract,
                                      monitoringNews: MonitoringNews)
                                     (implicit val exc: ExecutionContext)
  extends Controller {


  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus: Future[Unit] = setWebhook().map { x =>
    println(x.body)
  }

  implicit val formats: Formats = Serialization.formats(NoTypeHints) +
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


  def index = Action {
    Ok("lol")
  }

  def inbox = Action { request =>
    val js = request.body.asJson.get
    val update = fromJson[Update](js.toString)
    val response = update.message map { msg =>
      val command = getCommand(msg)
      println(command)
      command match {
        case Some("/start") => start(msg)
        case Some("/create_bid") => create_bid(msg)
        case _ =>
          update.callbackQuery.flatMap{ x =>
            val data = Json.parse(x.data.getOrElse("")).as[JsObject].value
            data.get("command").map(_.toString) flatMap {
              case "create_bid" => data.get("value").map(x => create_bid_choose_commodity(msg, x.toString))
            }
          }.getOrElse(SendMessage(Left(msg.chat.id), "Пока что я слишком слаб чтобы понять это"))
      }

    }

    response.map(x => Ok(toAnswerJson(x, x.methodName))).getOrElse(Ok("success"))
  }

  def start(msg: Message): SendMessage = {
    val buttons = Seq(
      Seq(
        KeyboardButton("Поделиться номером телефона", requestContact = Some(true))
      )
    )
    val keyboard = ReplyKeyboardMarkup(buttons, oneTimeKeyboard = Some(true))
    SendMessage(Left(msg.chat.id),
      "Я брокер-бот, мониторю торговые площадки, делаю прогнозы рынков," +
        " помогаю заключить контракт, организовываю торговые коммуникации." +
        "Для продожения мне необходим ваш номер телефона, его вы мне можете предоставить " +
        "просто кликнув по кнопке снизу.", replyMarkup = Some(keyboard))
  }

  def create_bid(msg: Message): SendMessage = {
    val cbDataSell = Json.obj("value" -> "sell", "command" -> "create_bid").toString()
    val cbDataBuy = Json.obj("value" -> "buy", "command" -> "create_bid").toString()
    val buttons = Seq(
      Seq(
        InlineKeyboardButton("Заявка на покупку", callbackData = Some(cbDataBuy)),
        InlineKeyboardButton("Заявка на продажу", callbackData = Some(cbDataSell))
      )
    )
    val keyboard = InlineKeyboardMarkup(buttons)
    SendMessage(Left(msg.chat.id), "Какой тип заявки вы хотите составить?", replyMarkup = Some(keyboard))
  }

  def create_bid_choose_commodity(msg: Message, value: String): SendMessage = {

    SendMessage(Left(msg.chat.id),
      "Я брокер-бот, мониторю торговые площадки, делаю прогнозы рынков," +
        " помогаю заключить контракт, организовываю торговые коммуникации")

  }

  def getCommand(msg: Message): Option[String] = {
    msg.entities.getOrElse(Seq()).find {
      me =>
        me.`type` == "bot_command" && me.offset == 0
    }.flatMap {
      me =>
        msg.text.map(_.slice(0, me.length))
    }
  }

  def setWebhook(): Future[WSResponse] = {

    val bodyParts = List(
      new StringPart("url", "https://52.174.38.160/webhook", "UTF-8"),
      new FilePart("certificate", new File(s"${
        conf.getString("filePrefix").get
      }public/certificates/nginx.crt"))
    )
    val client = ws.underlying.asInstanceOf[AsyncHttpClient]

    val builder = client.preparePost(url + "/setWebhook" +
      "")

    builder.setHeader("Content-Type", "multipart/form-data")
    bodyParts.foreach(builder.addBodyPart)

    val result = Promise[WSResponse]()

    client.executeRequest(builder.build(), new AsyncCompletionHandler[Response]() {
      override def onCompleted(response: Response): Response = {
        result.success(AhcWSResponse(response))
        response
      }

      override def onThrowable(t: Throwable): Unit = {
        result.failure(t)
      }
    })

    result.future
  }

  def genModel(pass: Option[String]) = Action {
    val res = for {
      p <- pass if p == "qwerty"
    } yield {
      slick.codegen.SourceCodeGenerator.main(
        Array(
          "slick.driver.PostgresDriver",
          "org.postgresql.Driver",
          "jdbc:postgresql://52.174.38.160:5432/tradehubbot",
          "app",
          "models",
          "bot",
          "root")
      )
      Ok("success gen")
    }
    res.getOrElse(BadRequest("access denied"))
  }
}
