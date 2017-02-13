package controllers

import java.io.File
import java.sql.{Date => DateSQL}
import java.util.Date
import javax.inject.Inject

import models.Tables.{BidsRow, UsersChatsRow}
import models._
import telegram._
import org.asynchttpclient.{AsyncCompletionHandler, AsyncHttpClient, Response}
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
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

import scala.util.{Failure, Success, Try}


class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration,
                                      company: Company, commodity: Commodity,
                                      bid: Bid, contract: Contract,
                                      monitoringNews: MonitoringNews,
                                      userChat: UserChat)
                                     (implicit val exc: ExecutionContext)
  extends Controller {


  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus: Future[Unit] = setWebhook().map { x =>
    println(x.body)
  }

  implicit val formats: Formats = Serialization.formats(NoTypeHints) +
    new EnumNameSerializer(ChatAction) +
    new EnumNameSerializer(ParseMode)

  def index = Action {
    Ok("Выкатывай")
  }

  def inbox = Action.async { request =>

    val js = request.body.asJson.get
    val update = fromJson[Update](js.toString)

    val response = (update.message, update.callbackQuery) match {
      case (Some(msg), None) =>
        val command = getCommand(msg)
        command match {
          case Some("/start") => start(msg.chat.id)
          case Some("/create_bid") if msg.from.isDefined =>
            create_bid_message(msg.chat.id, msg.from.get.id)
          case _ =>
            msg.replyToMessage match {
              case Some(x) => create_bid(msg.chat.id, msg, x)
              case None =>
                msg.contact match {
                  case Some(x) => store_contact(msg.chat.id, x)
                  case None => Future successful errorMsg(msg.chat.id)
                }
            }
        }

      case (None, Some(cbq)) =>
        val callbackData = cbq.data.map(_.split(";")) flatMap {
          case Array(c, v) => Some((c, v))
          case _ => None
        }
        callbackData match {
          case Some((cbCom, cbVal)) =>
            cbCom match {
              case "c_b1" => create_bid_choose_commodity(cbq.from.id, cbVal)
              case "c_b2" => create_bid_choose_partner(cbq.from.id, cbVal)
              case "c_b3" => create_bid_ask_conditions(cbq.from.id, cbVal)
              case "c_b4" => sendToExternal(cbq.from.id, cbVal)
            }
          case _ => Future successful errorMsg(cbq.from.id)

        }

    }
    response.map(x => Ok(toAnswerJson(x, x.methodName)))
  }

  def sendToExternal(chatId: Long, value: String): Future[SendMessage] = {
    val res = Try(value.toInt) match {
      case Success(cId) =>
        val res = ws.url("http://159.203.169.25/bot/webhook")
          .post(Json.obj("contract_id" -> JsNumber(cId)))
        res.map(x => println(x.body, x.status))
        res.map(_.status == 200)


      case Failure(_) =>
        Future successful false
    }
    res.map { r =>
      if (r)
        SendMessage(Left(chatId), "В скором времени с вами свяжется коммерческий представитель")
      else
        errorMsg(chatId)
    }
  }

  def start(chatId: Long): Future[SendMessage] = {
    val buttons = Seq(
      Seq(
        KeyboardButton("Поделиться номером телефона", requestContact = Some(true))
      )
    )
    val keyboard = ReplyKeyboardMarkup(buttons, oneTimeKeyboard = Some(true))

    Future successful {
      SendMessage(Left(chatId),
        """
          |Я брокер-бот, мониторю торговые площадки, делаю прогнозы рынков,
          |помогаю заключить контракт, организовываю торговые коммуникации.
          |
          |Для продожения мне необходим ваш номер телефона, его вы мне можете предоставить,
          |просто кликнув по кнопке снизу.
        """.stripMargin,
        replyMarkup = Some(keyboard))
    }
  }

  def create_bid(chatId: Long, msg: Message, repliedMsg: Message): Future[SendMessage] = {
    val contIdOpt = repliedMsg.text.flatMap(x =>
      Try {
        val s = "Номер в реестре: [0-9]*".r.findFirstIn(x).get
        s.substring(17, s.length).toInt
      }.toOption
    )
    val tOpt = repliedMsg.text.flatMap(x =>
      Try {
        val s = "Вы выступаете в роли .*".r.findFirstIn(x).get
        if (s.substring(21, s.length) == "продавца")
          "s"
        else
          "b"
      }.toOption
    )
    (contIdOpt, tOpt, msg.from) match {
      case (Some(contId), Some(t), Some(usr)) if t == "b" || t == "s" =>
        contract.get(contId).flatMap(
          _.map { cont =>
            val newBid = BidsRow(0, if (t == "b") cont.consumerId else cont.producerId,
              t == "s", cont.commodityId, msg.text.getOrElse("Подробности отсутствуют"))
            bid.insert(newBid).flatMap{r =>
              if (r > 0) {
                userChat.get(if (t == "b") cont.producerId else cont.consumerId).flatMap(
                  _.map { partner =>
                    val keyboard = InlineKeyboardMarkup(
                      Seq(Seq(InlineKeyboardButton("Принять предложение", Some(s"c_b4;$contId"))))
                    )
                    val partMsg = contract.getInfo(cont.contractId).flatMap(_.map { info =>
                      sendMessageToChat(
                        SendMessage(
                          Left(partner.chatId.toLong),
                          s"""
                             |Поступила заявка от ваших партнеров, компании ${if (t == "b") info._3 else info._4}
                             |Контракт №${info._1}
                             |Товар: ${info._2}
                             |Покупатель: ${info._3}
                             |Поставщик: ${info._4}
                             |
                             |С такими условиями:
                             |${msg.text.getOrElse("Подробности отсутствуют")}
                        """.stripMargin,
                          replyMarkup = Some(keyboard)
                        )
                      )
                    }.getOrElse(Future successful false)
                    )
                    partMsg.map(x =>
                      if (x)
                        SendMessage(Left(chatId), "Предложение принято и выслано вашим партнерам")
                      else
                        errorMsg(chatId)
                    )
                  }.getOrElse(Future successful errorMsg(chatId))
                )
              } else Future successful errorMsg(chatId)
          }
          }.getOrElse(Future successful errorMsg(chatId))
        )
      case _ => Future successful errorMsg(chatId)
    }


  }

  def create_bid_message(chatId: Long, userId: Long): Future[SendMessage] = {
    userChat.getUserCommodities(userId, isSell = true).zip(
      userChat.getUserCommodities(userId, isSell = false)
    ).zip(userChat.get(chatId))

      .map {
        case ((s, b), u) =>
          println(u)
          if (u.flatMap(_.contragentId).isDefined) {
            if (s.isEmpty && b.isEmpty)
              SendMessage(Left(chatId), "Рамковых договоров для вашей компании не найдено!")
            else {
              val buttons = Seq(
                Seq() ++
                  (if (s.nonEmpty)
                    Seq(InlineKeyboardButton("Заявка на продажу", callbackData = Some("c_b1;s")))
                  else Seq()) ++
                  (if (b.nonEmpty)
                    Seq(InlineKeyboardButton("Заявка на покупку", callbackData = Some("c_b1;b")))
                  else Seq())

              )
              val keyboard = InlineKeyboardMarkup(buttons)
              SendMessage(Left(chatId), "Какой тип заявки вы хотите составить?", replyMarkup = Some(keyboard))
            }
          }
          else
            SendMessage(Left(chatId),
              """
                |Так как мы не смогли связать вас с какой-лобо компанией,
                |эта функция для вас пока что недоступна.
                |
                |Если являетесь уполномоченым представителем какой-либо компании в нашем реестре,
                |то напишите об этом в поддержку, (комманда /feedback ) указав имя и номер телефона.
              """.stripMargin)
      }

  }

  def create_bid_choose_commodity(chatId: Long, value: String): Future[SendMessage] = {

    userChat.getUserCommodities(chatId, value == "s").map { commSeq =>
      val buttons =
        commSeq.map(c =>
          Seq(
            InlineKeyboardButton(c._2, Some(s"c_b2;${c._1.toString}:$value"))
          )
        )

      val keyboard = InlineKeyboardMarkup(buttons)
      SendMessage(Left(chatId),
        s"Выберите товар, который вы хотите ${if (value == "b") "купить" else "продать"}",
        replyMarkup = Some(keyboard)
      )
    }


  }

  def create_bid_choose_partner(chatId: Long, value: String): Future[SendMessage] = {

    value.split(":") match {
      case Array(commId, t) =>
        userChat.getPartners(chatId, commId.toInt, t == "s").map {
          comSeq =>
            val buttons =
              comSeq.map(c =>
                Seq(
                  InlineKeyboardButton(s"${c._2}, Контракт #${c._3} ", Some(s"c_b3;${c._4}:$t"))
                )
              )

            val keyboard = InlineKeyboardMarkup(buttons)
            SendMessage(Left(chatId),
              s"Выберите компанию для сотрудничетсва",
              replyMarkup = Some(keyboard)
            )
        }
    }
  }

  def create_bid_ask_conditions(chatId: Long, value: String): Future[SendMessage] = {
    Try((value.split(":").head.toInt, value.split(":").last)) match {
      case Success((contId, t)) if t == "b" || t == "s" =>
        contract.getInfo(contId).map(
          _.map { cont =>
            SendMessage(Left(chatId),
              s"""
                 |Вы хотите подать заявку по контракту №${cont._1}
                 |Вы выступаете в роли ${if (t == "b") "_покупателя_" else "_продавца_"}
                 |
                |Номер в реестре: _${contId}_
                 |Товар: ${cont._2}
                 |Покупатель: ${cont._3}
                 |Поставщик: ${cont._4}
                 |
                |Опишите условия сделки.
              """.stripMargin,
              replyMarkup = Some(ForceReply()), parseMode = Some(ParseMode.Markdown))
          }.getOrElse(errorMsg(chatId))
        )

      case Failure(_) =>
        Future successful errorMsg(chatId)
    }


  }

  def store_contact(chatId: Long, contact: Contact): Future[SendMessage] = {

    for {
      uc <- userChat.get(contact.phoneNumber)
      com <- company.get(contact.phoneNumber)
      insertRes <- {
        userChat.del(uc.map(_.id).getOrElse(-1)).flatMap { _ =>
          val newUser =
            UsersChatsRow(
              0, chatId.toInt, "", contact.phoneNumber, com.map(_.companyId), contact.firstName,
              contact.lastName, new DateSQL(new Date().getTime)
            )
          println(newUser)
          userChat.insert(newUser)
        }
      }
    } yield {
      val mkp = ReplyKeyboardRemove()
      if (insertRes >= 0) {
        if (uc.isDefined) {
          if (com.isDefined) {
            SendMessage(Left(chatId),
              s"""
                 |Этот номер телефона уже был зарегистрирован пользователем
                 |${uc.get.firstName} ${uc.get.lastName.getOrElse("")}
                 |${uc.get.dateRegistred}
                 |
                 |Вы выбраны представителем компании "${com.get.companyName}", так как
                 |этот номер телефона был указан при составлении контракта.
                 |
                 |Если вы обращаетесь к нашему боту впервые то напишите об этом в поддержку,
                 |(комманда /feedback ) указав имя и номер телефона.
          """.
                stripMargin,
              replyMarkup = Some(mkp)
            )
          } else {
            SendMessage(Left(chatId),
              s"""
                 |Этот номер телефона уже был зарегистрирован пользователем
                 |${uc.get.firstName} ${uc.get.lastName.getOrElse("")}
                 |${uc.get.dateRegistred}
                 |
                 |Если вы обращаетесь к нашему боту впервые то напишите об этом в поддержку,
                 |(комманда /feedback ) указав имя и номер телефона.
          """.stripMargin,
              replyMarkup = Some(mkp)
            )
          }
        }
        else if (com.isDefined) {
          SendMessage(Left(chatId),
            s"""
               |Поздровляем, вы успешно загестрировались под именем
               |${contact.firstName} ${contact.lastName.getOrElse("")}
               |
               |Вы выбраны представителем компании "${com.get.companyName}", так как
               |этот номер телефона был указан при составлении контракта.
               |
             |Если вы не имеете отношения к этой компании то напишите об этом в поддержку,
               |(комманда /feedback ) указав имя и номер телефона.
          """.stripMargin,
            replyMarkup = Some(mkp)
          )
        } else {
          SendMessage(Left(chatId),
            s"""
               |Поздровляем, вы успешно загестрировались под именем
               |${contact.firstName} ${contact.lastName.getOrElse("")}
               |
               |В нашей базе данных мы не нашли компаний, соответсвующих вашему номеру телефона,
               |поэтому некоторые функции бота для вас будут недоступны.
               |
               |Если являетесь уполномоченым представителем какой-либо компании в нашем реестре,
               |то напишите об этом в поддержку, (комманда /feedback ) указав имя и номер телефона.
          """.stripMargin,
            replyMarkup = Some(mkp)
          )
        }
      } else {
        errorMsg(chatId)
      }
    }

  }

  def sendMessageToChat(sendMsg: SendMessage): Future[Boolean] = {
    ws.url(url + "/sendMessage")
      .post(Json.parse(toJson(sendMsg).toString)).map(_.status == 200)
  }

  def errorMsg(chatId: Long): SendMessage = {
    SendMessage(Left(chatId),
      """
        |Возникла проблема.
        |Пожалуйста, напишите об этом в поддержку, (комманда /feedback ) указав имя и номер телефона.
      """.stripMargin
    )
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

  def toJson[T](t: T): String = compact(render4s(Extraction.decompose(t).underscoreKeys))

  def toAnswerJson[T](t: T, method: String): JsValue =
    Json.parse(
      compact(
        render4s(new JObject(Extraction.decompose(t).asInstanceOf[JObject].obj ++
          List("method" -> JString(method))).underscoreKeys)
      )
    )

  def fromJson[T: Manifest](json: String): T = parse4s(json).camelizeKeys.extract[T]

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
