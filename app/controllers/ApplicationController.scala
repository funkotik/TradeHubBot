package controllers

import java.io.File
import javax.inject.{Inject, Singleton}

import com.pengrad.telegrambot.TelegramBotAdapter
import com.pengrad.telegrambot.request.SetWebhook
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, Controller, MultipartFormData}

import scala.concurrent.{ExecutionContext, Future}

//@Singleton
class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration)
                                     (implicit val exc: ExecutionContext)
  extends Controller {

  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus = setWebhook

  def index = Action {
    Ok("lol")
  }

  def log(any: String) = Action { request =>
    println(any, request)
    Ok("kek")
  }

  def setWebhook = {
    val bot = TelegramBotAdapter.build(conf.getString("token").get)
    val request = new SetWebhook()
      .url("url")
      .certificate(new File("public/certificates/nginx.crt"))
    bot.execute(request)
  }
}
