package controllers

import java.io.File
import java.sql.{Date => DateSQL}
import java.util.Date
import javax.inject.Inject

import models.Tables.{BidsRow, UsersChatsRow}
import models._
import org.asynchttpclient.Realm.AuthScheme
import telegram._
import org.asynchttpclient.{AsyncCompletionHandler, AsyncHttpClient, Response}
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}
import org.asynchttpclient.request.body.multipart.{FilePart, StringPart}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import play.api.mvc.{Action, AnyContent, Controller}
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.JsonMethods.{parse => parse4s, render => render4s, _}
import telegram.methods._
import org.json4s.ext.EnumNameSerializer
import play.api.cache.CacheApi
import play.api.libs.ws.ahc.AhcWSResponse

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


class ApplicationController @Inject()(ws: WSClient, conf: play.api.Configuration,
                                      company: Company, commodity: Commodity,
                                      bid: Bid, contract: Contract,
                                      monitoringNews: MonitoringNews,
                                      userChat: UserChat, cache: CacheApi,
                                      tenders: Tenders, analytics: Analytics)
                                     (implicit val exc: ExecutionContext)
  extends Controller {


  val url = s"https://api.telegram.org/bot${conf.getString("token").get}"

  val webhookStatus: Future[Unit] = setWebhook().map { x =>
    println(x.body)
  }

  implicit val formats: Formats = Serialization.formats(NoTypeHints) +
    new EnumNameSerializer(ChatAction) +
    new EnumNameSerializer(ParseMode)

  def index: Action[AnyContent] = Action { request =>
    //    setWebhook().map { x =>
    //      println(x.body)
    //    }
    //    analytics.getAnalytics.map { x => Ok(x.toString) }

    Ok("ok")
  }

  def refreshTenders = Action {
    //    tenders.getAllTenders(commercial = true).flatMap { n =>
    //    tenders.getAllTenders(commercial = false, Some(new DateTime(2017, 2, 1, 0, 0, DateTimeZone.UTC))).recover { case e => e.printStackTrace() }
    //  }
    Ok("kek")
  }

  def tender(query: String): Action[AnyContent] = Action.async {
    tenders.find(split(query)).map(x => Ok(views.html.tenderList(x)))
  }

  def inbox: Action[AnyContent] = Action.async { request =>

    val js = request.body.asJson.get
    val update = fromJson[Update](js.toString)

    val response = (update.message, update.callbackQuery) match {
      case (Some(msg), None) =>
        val mode = cache.get[Int](s"contract_mode:${msg.chat.id}")
        val command = getCommand(msg)
        command match {
          case Some("/start") =>
            start(msg.chat.id)
          case Some("/create_bid") if msg.from.isDefined =>
            create_bid_message(msg.chat.id, msg.from.get.id)
          case Some("/feedback") =>
            feedback_message(msg.chat.id)
          case Some("/monitoring") =>
            monitoring_message(msg.chat.id)
          case Some("/monitoring_news") =>
            monitoring_news_message(msg.chat.id)
          case Some("/contract") if mode.isDefined =>
            askQuantity(msg.chat.id, mode.get)
          case Some("/quit") if mode.isDefined =>
            quitContract(msg.chat.id, mode.get)
          case _ =>
            msg.replyToMessage match {
              case Some(x) if msg.text.isDefined =>
                process_reply(msg, x, mode)
              case _ =>
                msg.contact match {
                  case Some(x) => store_contact(msg.chat.id, x)
                  case None if mode.isDefined => redirectMessageToPartner(msg, mode.get)
                  case _ => Future successful SendMessage(Left(msg.chat.id), "Я не понимаю этой комманды")
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
              case "c_b4" => startBidDialog(cbq.from.id, cbVal)
              case "c_b5" => confirmBidCreation(cbq.from.id, cbVal)
              case "c_b6" => processTax(cbq.from.id, cbVal)
              case "ms" => monitoring_stop(cbq.from.id, cbVal)
            }
          case _ => Future successful errorMsg(cbq.from.id)

        }

    }
    response.map { x =>
      if (x.chatId.isLeft) {
        Ok(toAnswerJson(x, x.methodName))
      } else Ok
    }
  }

  def askQuantity(chatId: Long, bidId: Int): Future[SendMessage] = {
    sendMessageToChat(SendMessage(Left(chatId), "Введите количество едениц товара", replyMarkup = Some(ForceReply()))).map { mid =>
      cache.set(s"reply:$chatId:$mid", "quantity")
      SendMessage(Left(chatId), "Например просто \"500\" или \"0.2\"")
    }
  }

  def askPrice(chatId: Long): Future[SendMessage] = {
    sendMessageToChat(SendMessage(Left(chatId),
      s"""Укажите цену за единицу в гривнах.""".stripMargin,
      replyMarkup = Some(ForceReply()))).map { mid =>
      cache.set(s"reply:$chatId:$mid", "price")
      SendMessage(Left(chatId), "Например просто \"12543.2\" или \"10\"")
    }
  }

  def askTax(chatId: Long, bidId: Int): Future[SendMessage] = {

    val keyboard = InlineKeyboardMarkup(
      Seq(
        Seq(InlineKeyboardButton("Да", Some(s"c_b6;y:$bidId"))),
        Seq(InlineKeyboardButton("Нет", Some(s"c_b6;n:$bidId")))
      )
    )
    Future successful SendMessage(Left(chatId), "Поставка будет с НДС?", replyMarkup = Some(keyboard))
  }

  def askDate(chatId: Long, redisKey: String): Future[SendMessage] = {
    sendMessageToChat(SendMessage(Left(chatId),
      s"""Укажите дату в формате ДД.ММ.ГГГГ .""".stripMargin,
      replyMarkup = Some(ForceReply()))).map { mid =>
      cache.set(s"reply:$chatId:$mid", redisKey)
      SendMessage(Left(chatId), "Например \"01.04.2017\" или \"27.06.2018\"")
    }
  }

  def processTax(chatId: Long, value: String): Future[SendMessage] = {
    val ans = value.split(":").head
    val bidId = value.split(":").last
    cache.set(s"contract:$bidId:tax", value == "y")
    sendMessageToChat(SendMessage(Left(chatId), "Укажите дату поставки.")).flatMap { _ =>
      askDate(chatId, "ship_date")
    }
  }

  def processShipDate(msg: Message, bidId: Int): Future[SendMessage] = {
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    Try(formatter.parseLocalDate(msg.text.getOrElse(""))) match {
      case Success(x) =>
        cache.set(s"contract:$bidId:ship_date", x.toDate)
        sendMessageToChat(SendMessage(Left(msg.chat.id), "Укажите дату оплаты.")).flatMap { _ =>
          askDate(msg.chat.id, "pay_date")
        }
      case Failure(_) =>
        sendMessageToChat(SendMessage(Left(msg.chat.id), "Ошибка распознавания даты. Попробуйте снова."))
        askDate(msg.chat.id, "ship_date")
    }
  }

  def processPayDate(msg: Message, bidId: Int): Future[SendMessage] = {
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    Try(formatter.parseLocalDate(msg.text.getOrElse(""))) match {
      case Success(x) =>
        cache.set(s"contract:$bidId:pay_date", x.toDate)
        confirmContract(msg.chat.id, bidId)
      case Failure(_) =>
        sendMessageToChat(SendMessage(Left(msg.chat.id), "Ошибка распознавания даты. Попробуйте снова."))
        askDate(msg.chat.id, "ship_date")
    }
  }

  def processQuantity(msg: Message, bidId: Int): Future[SendMessage] = {
    msg.text.flatMap(x => Try(x.replace(",", ".").toDouble).toOption) match {
      case Some(x) =>
        cache.set(s"contract:$bidId:quantity", x)
        sendMessageToChat(SendMessage(Left(msg.chat.id), s"Вы указали что цена за единицу будет $x грн")).flatMap { _ =>
          askPrice(msg.chat.id)
        }
      case None =>
        sendMessageToChat(SendMessage(Left(msg.chat.id), "Я не смог распознать число, которое вы ввели, попробуйте снова")).flatMap { _ =>
          askQuantity(msg.chat.id, bidId)
        }
    }
  }

  def processPrice(msg: Message, bidId: Int): Future[SendMessage] = {
    msg.text.flatMap(x => Try(x.replace(",", ".").toDouble).toOption) match {
      case Some(x) =>
        cache.set(s"contract:$bidId:price", x)
        askTax(msg.chat.id, bidId)
      case None =>
        sendMessageToChat(SendMessage(Left(msg.chat.id), "Я не смог распознать число, которое вы ввели, попробуйте снова")).flatMap { _ =>
          askPrice(msg.chat.id)
        }
    }
  }

  def confirmContract(chatId: Long, bidId: Int): Future[SendMessage] = {
    bid.getWithChats(bidId).map {
      case Some((b, cont, comm, prod, cons, prodComp, consComp)) =>
        for {
          tax <- cache.get[Boolean](s"contract:$bidId:tax")
          price <- cache.get[Double](s"contract:$bidId:price")
          quantity <- cache.get[Double](s"contract:$bidId:quantity")
          shipDate <- cache.get[Date](s"contract:$bidId:ship_date")
          payDate <- cache.get[Date](s"contract:$bidId:pay_date")
        } yield {
          val taxVal = if(tax) quantity * price * 0.2 else 0
          val total = quantity * price + taxVal
          val msgText =
            s"""
              |Товар: ${comm.name}
              |Заказчик: ${consComp.companyName}
              |Поставщик: ${prodComp.companyName}
              |
              |Дата поставки: ${shipDate.toString}
              |Дата оплаты: ${payDate.toString}
              |
              |Количество единиц: $quantity
              |Цена за еденицу: $price
              |НДС: $taxVal
              |
              |Итого: $total
            """.stripMargin
          val buttons = Seq(
            Seq(
              InlineKeyboardButton("Согласен", callbackData = Some(s"c_b7;y:${
                b.id
              }"))
            ),
            Seq(
              InlineKeyboardButton("Не согласен", callbackData = Some(s"c_b7;n:${
                b.id
              }"))
            )
          )
          val keyboard = InlineKeyboardMarkup(buttons)
          sendMessageToChat(SendMessage(Left(prod.chatId), msgText, replyMarkup = Some(keyboard))).zip(
            sendMessageToChat(SendMessage(Left(cons.chatId), msgText, replyMarkup = Some(keyboard)))
          ).map {
            case (p, c) =>
              cache.set(s"confirm_contract:${b.id}", (p, c))
            case _ =>
              errorMsg(chatId)
          }
        }
        SendMessage(Right(""), "")
    }
  }

  def quitContract(chatId: Long, bidId: Int): Future[SendMessage] = {
    bid.getWithChats(bidId).map {
      case Some((b, cont, comm, prod, cons, prodComp, consComp)) =>
        val recChatId = if (chatId == prod.chatId) cons.chatId else prod.chatId
        cache.remove(s"contract_mode:$chatId")
        sendMessageToChat(SendMessage(Left(recChatId), "Ваш партнер покинул диалог"))
        SendMessage(Left(chatId), "Вы вышли из диалога")
      case _ => errorMsg(chatId)
    }
  }

  def confirmBidCreation(chatId: Long, value: String): Future[SendMessage] = {
    val rsp = value.split(":")
    val answer = rsp.head
    val bidId = rsp.last.toInt
    bid.getWithChats(bidId).map {
      case Some((b, cont, comm, prod, cons, prodComp, consComp)) =>
        cache.get[(Int, Int)](s"confirm_bid:${b.id}").foreach {
          case (pMId, cMId) =>
            val msgText = if (chatId == prod.chatId) {
              bid.updateStatus(b.id, answer == "y", prod = true)

              s"""
                 |Обе стороны подтвердили свою заинтересованность по созданию дополнителного соглашения по котракту №${cont.contractNumber}
                 |по продаже товара "${comm.name}", где поставщиком выступает компания "${prodComp.companyName}", а заказчиком - компания "${consComp.companyName}".
                 |Вы подтверждаете свою заинтересованность?
                 |Ответом "Да" вы подтверджаете то, что я стану вашим коммерческим предсавитилем в этой поставке.
                 |"${prodComp.companyName}" - ${if (answer == "y") "Подтверждено" else "Отменено"}
                 |"${consComp.companyName}" - ${b.consumerConfirmed.map(if (_) "Подтверждено" else "Отменено").getOrElse("Ожидание ответа")}
                """.stripMargin

            } else {
              bid.updateStatus(b.id, answer == "y", prod = false)
              s"""
                 |Обе стороны подтвердили свою заинтересованность по созданию дополнителного соглашения по котракту №${cont.contractNumber}
                 |по продаже товара "${comm.name}", где поставщиком выступает компания "${prodComp.companyName}", а заказчиком - компания "${consComp.companyName}".
                 |Вы подтверждаете свою заинтересованность?
                 |Ответом "Да" вы подтверджаете то, что я стану вашим коммерческим предсавитилем в этой поставке.
                 |"${prodComp.companyName}" - ${b.producerConfirmed.map(if (_) "Подтверждено" else "Отменено").getOrElse("Ожидание ответа")}
                 |"${consComp.companyName}" - ${if (answer == "y") "Подтверждено" else "Отменено"}
                   """.stripMargin
            }
            val buttons = Seq(
              Seq(
                InlineKeyboardButton("Да (Начать оформление дополнительного соглашения)", callbackData = Some(s"c_b5;y:${
                  b.id
                }"))
              ),
              Seq(
                InlineKeyboardButton("Нет (Отклонить предложение)", callbackData = Some(s"c_b5;n:${
                  b.id
                }"))
              )
            )
            val keyboard = InlineKeyboardMarkup(buttons)
            editMessageInChat(EditMessageText(Some(Left(prod.chatId)), Some(pMId), text = msgText, replyMarkup = Some(keyboard)))
            editMessageInChat(EditMessageText(Some(Left(cons.chatId)), Some(cMId), text = msgText, replyMarkup = Some(keyboard)))


            if ((if (chatId == prod.chatId) b.consumerConfirmed.getOrElse(false) else b.producerConfirmed.getOrElse(false)) && answer == "y") {
              val msgText =
                """Хорошо, теперь начинаем оформление дополнительного соглашения к договору.
                  |Все сообщения, которые вы пишите, будут транслироваться в чат к вашему партнеру.
                  |Когда вы будете готовы - введите комманду /contract
                  |
                  |Для выхода из режима заключения заявки - введите /quit
                """.stripMargin
              sendMessageToChat(SendMessage(Left(prod.chatId), msgText))
              sendMessageToChat(SendMessage(Left(cons.chatId), msgText))
              cache.set(s"contract_mode:${prod.chatId}", b.id)
              cache.set(s"contract_mode:${cons.chatId}", b.id)
            }
        }
    }

    Future successful SendMessage(Right(""), "")
  }

  def redirectMessageToPartner(msg: Message, bidId: Int): Future[SendMessage] = {
    bid.getWithChats(bidId).map {
      case Some((b, cont, comm, prod, cons, prodComp, consComp)) =>
        val recChatId = if (msg.chat.id == prod.chatId) cons.chatId else prod.chatId
        redirectMessageToChat(ForwardMessage(Left(recChatId), Left(msg.chat.id), messageId = msg.messageId))
        SendMessage(Right(""), "")
    }
  }

  def startBidDialog(chatId: Long, value: String): Future[SendMessage] = {

    bid.getWithChats(value.toInt).flatMap {
      case Some((b, cont, comm, prod, cons, prodComp, consComp)) =>
        val buttons = Seq(
          Seq(
            InlineKeyboardButton("Да (Начать оформление дополнительного соглашения)", callbackData = Some(s"c_b5;y:${
              b.id
            }"))
          ),
          Seq(
            InlineKeyboardButton("Нет (Отклонить предложение)", callbackData = Some(s"c_b5;n:${
              b.id
            }"))
          )
        )
        val keyboard = InlineKeyboardMarkup(buttons)
        val msgText =
          s"""
             |Обе стороны подтвердили свою заинтересованность по созданию дополнителного соглашения по котракту №${cont.contractNumber}
             |по продаже товара "${comm.name}", где поставщиком выступает компания "${
            prodComp.companyName
          }", а заказчиком - компания "${
            consComp.companyName
          }".
             |Вы подтверждаете свою заинтересованность?
             |Ответом "Да" вы подтверджаете то, что я стану вашим коммерческим предсавитилем в этой поставке.
             |"${prodComp.companyName}" - Ожидание ответа
             |"${consComp.companyName}" - Ожидание ответа
              """.stripMargin
        sendMessageToChat(SendMessage(Left(prod.chatId), msgText, replyMarkup = Some(keyboard))).zip(
          sendMessageToChat(SendMessage(Left(cons.chatId), msgText, replyMarkup = Some(keyboard)))
        ).map {
          case (p, c) =>
            cache.set(s"confirm_bid:${
              b.id
            }", (p, c))
            SendMessage(Right(""), "")
          case _ =>
            errorMsg(chatId)
        }
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

  def create_feedback_request(msg: Message): Future[SendMessage] = {
    val supportChatId = conf.getLong("supportChatId").get
    sendMessageToChat(
      SendMessage(
        Left(supportChatId),
        s"""
           |От пользователя ${msg.from.map(x => x.firstName + " " + x.lastName.getOrElse("")).getOrElse("НЕИЗВЕСНО")}:
           |${msg.text.getOrElse("")}
        """.stripMargin
      )
    ).map {
      mId =>
        cache.set(s"reply:$supportChatId:$mId", s"feedback_response:${msg.chat.id}:${msg.messageId}")
        println(s"feedback_response:$supportChatId:${msg.messageId}")
        SendMessage(Left(msg.chat.id), s"Ваше сообщение отпралено.")
    }
  }

  def monitoring_news_message(chatId: Long): Future[SendMessage] = {
    val mkp = ForceReply()
    sendMessageToChat(
      SendMessage(Left(chatId),
        """
          |Выберите интересующие вас товары.
        """.stripMargin,
        replyMarkup = Some(mkp)
      )
    ).map {
      mId =>
        cache.set(s"reply:$chatId:$mId", "monitoring_news")
        SendMessage(Left(chatId),
          """
            |Команда "\monitoring_news" позволяет вам отслеживать тендера с площадок,
            |таких как "Rialto" и "ProZorro".
          """.stripMargin)
    }
  }

  def monitoring_stop_message(chatId: Long): Future[SendMessage] = {

    monitoringNews.get(chatId).map {
      mnSeq =>

        val btns = Seq(mnSeq.map {
          x =>
            InlineKeyboardButton(x.keywords, Some(s"ms;${
              x.id
            }"))
        })
        val mkp = InlineKeyboardMarkup(btns)

        SendMessage(Left(chatId),
          """
            |От какой подписки вы хотите отказаться?
          """.stripMargin, replyMarkup = Some(mkp))
    }
  }

  def monitoring_stop(chatId: Long, value: String): Future[SendMessage] = {
    monitoringNews.del(value.toInt).map {
      n =>
        if (n > 0)
          SendMessage(Left(chatId), "Подписка удалена.")
        else
          errorMsg(chatId)
    }
  }

  def monitoring_message(chatId: Long): Future[SendMessage] = {
    val mkp = ForceReply()
    sendMessageToChat(
      SendMessage(Left(chatId),
        """
          |Выберите интересующие вас товары.
        """.stripMargin,
        replyMarkup = Some(mkp)
      )
    ).map {
      mId =>
        cache.set(s"reply:$chatId:$mId", "monitoring")
        SendMessage(Left(chatId),
          """
            |Команда "мониторинг" позволяет вам отслеживать тендера с площадок,
            |таких как "Rialto" и "ProZorro".
          """.stripMargin)
    }
  }

  def monitoring_news(chatId: Long, query: String): Future[SendMessage] = {
    val text = split(query)
    if (text.nonEmpty) {
      monitoringNews.add_subscription(chatId, text.mkString(",")).map {
        n =>
          if (n > 0) {
            SendMessage(Left(chatId),
              s"""
                 |По запросу "${
                text.mkString("\", \"")
              }" успешно добавлен мониторинг.
            """.stripMargin
            )
          }
          else errorMsg(chatId)
      }
    } else {
      Future successful SendMessage(Left(chatId), "Задан пустой поисковый запрос")
    }
  }

  def monitoring(chatId: Long, query: String): Future[SendMessage] = {
    val keyboard = InlineKeyboardMarkup(Seq(Seq(InlineKeyboardButton("Подробнее", url = Some(conf.getString("host").get + routes.ApplicationController.tender(query).url)))))
    val text = split(query)
    if (text.isEmpty) {
      Future successful SendMessage(Left(chatId), "Задан пустой поисковый запрос")
    }
    else {
      tenders.find(text, commercial = Some(false)).flatMap {
        x =>
          tenders.find(text, commercial = Some(true)).map {
            n =>
              SendMessage(Left(chatId),
                s"""
                   |По запросу "${
                  text.mkString("\", \"")
                }" найдено:
                   |${
                  n.length
                } тендеров на Rialto
                   |${
                  x.length
                } тендеров на ProZorro
        """.
                  stripMargin,
                replyMarkup = Some(keyboard)
              )
          }
      }
    }
  }

  def feedback_message(chatId: Long): Future[SendMessage] = {
    val mkp = ForceReply()
    sendMessageToChat(
      SendMessage(Left(chatId),
        """
          |Напишите пожалуйста ваш отзыв или предложение.
        """.stripMargin,
        replyMarkup = Some(mkp)
      )
    ).map {
      mId =>
        cache.set(s"reply:$chatId:$mId", "feedback_request")
        SendMessage(Left(chatId), "Служба поддержки ответит вам в скором сремени")
    }
  }

  def feedback_redirect_answer(msg: Message, chatId: Long, mId: Long): Future[SendMessage] = {
    Future successful SendMessage(Left(chatId), "Ответ от поддержки: " + msg.text.getOrElse(""), replyToMessageId = Some(mId))
  }

  def create_bid(chatId: Long, msg: Message, contIdOpt: Option[Int], tOpt: Option[String]): Future[SendMessage] = {

    (contIdOpt, tOpt, msg.from) match {
      case (Some(contId), Some(t), Some(usr)) if t == "b" || t == "s" =>
        contract.get(contId).flatMap(
          _.map {
            cont =>
              val newBid = BidsRow(0, if (t == "b") cont.consumerId else cont.producerId,
                msg.text.getOrElse("Подробности отсутствуют"), contId)
              bid.insert(newBid).flatMap {
                r =>
                  println(r)
                  if (r > 0) {
                    userChat.get(if (t == "b") cont.producerId else cont.consumerId).flatMap(
                      _.map {
                        partner =>
                          val keyboard = InlineKeyboardMarkup(
                            Seq(Seq(InlineKeyboardButton("Принять предложение", Some(s"c_b4;$r"))))
                          )
                          val partMsg = contract.getInfo(cont.contractId).flatMap(_.map {
                            info =>
                              sendMessageToChat(
                                SendMessage(
                                  Left(partner.chatId.toLong),
                                  s"""
                                     |Поступила заявка от ваших партнеров, компании ${
                                    if (t == "b") info._3 else info._4
                                  }
                                     |Контракт №${
                                    info._1
                                  }
                                     |Товар: ${
                                    info._2
                                  }
                                     |Покупатель: ${
                                    info._3
                                  }
                                     |Поставщик: ${
                                    info._4
                                  }
                                     |
                             |С такими условиями:
                                     |${
                                    msg.text.getOrElse("Подробности отсутствуют")
                                  }
                        """.stripMargin,
                                  replyMarkup = Some(keyboard)
                                )
                              )
                          }.getOrElse(Future successful 0)
                          )
                          partMsg.map(x =>
                            if (x > 0)
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
    val res =
      for {
        u <- userChat.get(chatId)
      } yield {
        val s = Await.result(userChat.getUserCommodities(userId, isSell = true), Duration.Inf)
        val b = Await.result(userChat.getUserCommodities(userId, isSell = false), Duration.Inf)
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
            println(4)
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
    res recover {
      case e => e.printStackTrace()
    }
    res
  }

  def create_bid_choose_commodity(chatId: Long, value: String): Future[SendMessage] = {

    userChat.getUserCommodities(chatId, value == "s").map {
      commSeq =>
        val buttons =
          commSeq.map(c =>
            Seq(
              InlineKeyboardButton(c._2, Some(s"c_b2;${
                c._1.toString
              }:$value"))
            )
          )

        val keyboard = InlineKeyboardMarkup(buttons)
        SendMessage(Left(chatId),
          s"Выберите товар, который вы хотите ${
            if (value == "b") "купить" else "продать"
          }",
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
                  InlineKeyboardButton(s"${
                    c._2
                  }, Контракт #${
                    c._3
                  } ", Some(s"c_b3;${
                    c._4
                  }:$t"))
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
          _.map {
            cont =>
              sendMessageToChat(SendMessage(Left(chatId), "Опишите условия сделки", replyMarkup = Some(ForceReply()))).map(
                mId => cache.set(s"reply:$chatId:$mId", s"create_bid:$contId:$t")
              )
              SendMessage(Left(chatId),
                s"""
                   |Вы хотите подать заявку по контракту №${
                  cont._1
                }
                   |Вы выступаете в роли ${
                  if (t == "b") "_покупателя_" else "_продавца_"
                }
                   |
                 |Товар: ${
                  cont._2
                }
                   |Покупатель: ${
                  cont._3
                }
                   |Поставщик: ${
                  cont._4
                }
              """.stripMargin,
                parseMode = Some(ParseMode.Markdown))
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
        userChat.del(uc.map(_.id).getOrElse(-1)).flatMap {
          _ =>
            val newUser =
              UsersChatsRow(
                0, chatId, contact.userId, contact.phoneNumber, com.map(_.companyId), contact.firstName,
                contact.lastName, uc.map(_.dateRegistred).getOrElse(new DateSQL(new Date().getTime))
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
                 |${
                uc.get.firstName
              } ${
                uc.get.lastName.getOrElse("")
              }
                 |${
                uc.get.dateRegistred
              }
                 |
                 |Вы выбраны представителем компании "${
                com.get.companyName
              }", так как
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
                 |${
                uc.get.firstName
              } ${
                uc.get.lastName.getOrElse("")
              }
                 |${
                uc.get.dateRegistred
              }
                 |
                 |В нашей базе данных мы не нашли компаний, соответсвующих вашему номеру телефона,
                 |поэтому функция /create_bid (составление предварительной заявки на поставку товара)
                 |для вас будет недоступна до того, как мы зарегистрируем вашу компанию.
                 |После этого вам останеться снова ввести комманду /start.
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
               |Поздровляем, вы успешно зарегистрировались под именем
               |${
              contact.firstName
            } ${
              contact.lastName.getOrElse("")
            }
               |
               |Вы выбраны представителем компании "${
              com.get.companyName
            }", так как
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
               |Поздровляем, вы успешно зарегистрировались под именем
               |${
              contact.firstName
            } ${
              contact.lastName.getOrElse("")
            }
               |
               |В нашей базе данных мы не нашли компаний, соответсвующих вашему номеру телефона,
               |поэтому функция /create_bid (составление предварительной заявки на поставку товара)
               |для вас будет недоступна до того, как мы зарегистрируем вашу компанию.
               |После этого вам останеться снова ввести комманду /start.
               |
               |Если вы являетесь уполномоченым представителем какой-либо компании в нашем реестре,
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

  def process_reply(msg: Message, replyTo: Message, mode: Option[Int]): Future[SendMessage] = {

    val cv = cache.get[String](s"reply:${msg.chat.id}:${replyTo.messageId}")
    println(s"reply:${msg.chat.id}:${replyTo.messageId}")
    println(cv)
    cv.map(_.split(":").toList) map {
      case "create_bid" :: chatId :: t :: Nil =>
        create_bid(msg.chat.id, msg, Try(chatId.toInt).toOption, Some(t))

      case "feedback_request" :: Nil =>
        create_feedback_request(msg)

      case "feedback_response" :: chatId :: mId :: Nil =>
        feedback_redirect_answer(msg, chatId.toLong, mId.toLong)

      case "monitoring" :: Nil =>
        monitoring(msg.chat.id, msg.text.get)

      case "quantity" :: Nil if mode.isDefined =>
        processQuantity(msg, mode.get)

      case "price" :: Nil if mode.isDefined =>
        processPrice(msg, mode.get)

      case "ship_date" :: Nil if mode.isDefined =>
        processShipDate(msg, mode.get)

      case "pay_date" :: Nil if mode.isDefined =>
        processPayDate(msg, mode.get)


    } getOrElse (Future successful errorMsg(msg.chat.id))
  }

  def redirectMessageToChat(msg: ForwardMessage): Future[Int] = {
    ws.url(url + "/forwardMessage")
      .post(Json.parse(toJson(msg).toString)).map {
      x => (x.json \ "result" \ "message_id").as[Int]
    }
  }

  def sendMessageToChat(sendMsg: SendMessage): Future[Int] = {
    ws.url(url + "/sendMessage")
      .post(Json.parse(toJson(sendMsg).toString)).map {
      x => (x.json \ "result" \ "message_id").as[Int]
    }
  }

  def editMessageInChat(editMsg: EditMessageText): Future[Int] = {
    ws.url(url + "/editMessageText")
      .post(Json.parse(toJson(editMsg).toString)).map {
      x => (x.json \ "result" \ "message_id").as[Int]
    }
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

    val builder = client.preparePost(url + "/setWebhook")

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

  def split(s: String): Seq[String] = {
    s.split(Array('.', ';', ',', ';', '(', ')')).map(_.trim).filter(_.nonEmpty)
  }

  def toJson[T](t: T): String = compact(render4s(Extraction.decompose(t).underscoreKeys))

  def toAnswerJson[T](t: T, method: String): JsValue = Json.parse(
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
