package models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.driver.PostgresDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.driver.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(Bids.schema, Commodities.schema, Companies.schema, Contracts.schema, MonitoringNews.schema, TgAddresses.schema, UsersChats.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Bids
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param authorId Database column author_id SqlType(int4)
   *  @param issell Database column issell SqlType(bool)
   *  @param commodityId Database column commodity_id SqlType(int4)
   *  @param note Database column note SqlType(text) */
  case class BidsRow(id: Int, authorId: Int, issell: Boolean, commodityId: Int, note: String)
  /** GetResult implicit for fetching BidsRow objects using plain SQL queries */
  implicit def GetResultBidsRow(implicit e0: GR[Int], e1: GR[Boolean], e2: GR[String]): GR[BidsRow] = GR{
    prs => import prs._
    BidsRow.tupled((<<[Int], <<[Int], <<[Boolean], <<[Int], <<[String]))
  }
  /** Table description of table bids. Objects of this class serve as prototypes for rows in queries. */
  class Bids(_tableTag: Tag) extends Table[BidsRow](_tableTag, "bids") {
    def * = (id, authorId, issell, commodityId, note) <> (BidsRow.tupled, BidsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(authorId), Rep.Some(issell), Rep.Some(commodityId), Rep.Some(note)).shaped.<>({r=>import r._; _1.map(_=> BidsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column author_id SqlType(int4) */
    val authorId: Rep[Int] = column[Int]("author_id")
    /** Database column issell SqlType(bool) */
    val issell: Rep[Boolean] = column[Boolean]("issell")
    /** Database column commodity_id SqlType(int4) */
    val commodityId: Rep[Int] = column[Int]("commodity_id")
    /** Database column note SqlType(text) */
    val note: Rep[String] = column[String]("note")

    /** Foreign key referencing Commodities (database name bids_commodities_commodity_id_fk) */
    lazy val commoditiesFk = foreignKey("bids_commodities_commodity_id_fk", commodityId, Commodities)(r => r.commodityId, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Companies (database name bids_companies_company_id_fk) */
    lazy val companiesFk = foreignKey("bids_companies_company_id_fk", authorId, Companies)(r => r.companyId, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Bids */
  lazy val Bids = new TableQuery(tag => new Bids(tag))

  /** Entity class storing rows of table Commodities
   *  @param commodityId Database column commodity_id SqlType(serial), AutoInc, PrimaryKey
   *  @param name Database column name SqlType(varchar), Length(256,true) */
  case class CommoditiesRow(commodityId: Int, name: String)
  /** GetResult implicit for fetching CommoditiesRow objects using plain SQL queries */
  implicit def GetResultCommoditiesRow(implicit e0: GR[Int], e1: GR[String]): GR[CommoditiesRow] = GR{
    prs => import prs._
    CommoditiesRow.tupled((<<[Int], <<[String]))
  }
  /** Table description of table commodities. Objects of this class serve as prototypes for rows in queries. */
  class Commodities(_tableTag: Tag) extends Table[CommoditiesRow](_tableTag, "commodities") {
    def * = (commodityId, name) <> (CommoditiesRow.tupled, CommoditiesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(commodityId), Rep.Some(name)).shaped.<>({r=>import r._; _1.map(_=> CommoditiesRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column commodity_id SqlType(serial), AutoInc, PrimaryKey */
    val commodityId: Rep[Int] = column[Int]("commodity_id", O.AutoInc, O.PrimaryKey)
    /** Database column name SqlType(varchar), Length(256,true) */
    val name: Rep[String] = column[String]("name", O.Length(256,varying=true))
  }
  /** Collection-like TableQuery object for table Commodities */
  lazy val Commodities = new TableQuery(tag => new Commodities(tag))

  /** Entity class storing rows of table Companies
   *  @param companyId Database column company_id SqlType(serial), AutoInc, PrimaryKey
   *  @param idNumber Database column id_number SqlType(varchar), Length(50,true)
   *  @param directorName Database column director_name SqlType(varchar), Length(256,true), Default(None)
   *  @param bankAccount Database column bank_account SqlType(varchar), Length(20,true)
   *  @param `e-mail` Database column e-mail SqlType(varchar), Length(256,true)
   *  @param telephone Database column telephone SqlType(varchar), Length(50,true)
   *  @param companyName Database column company_name SqlType(varchar), Length(255,true) */
  case class CompaniesRow(companyId: Int, idNumber: String, directorName: Option[String] = None, bankAccount: String, `e-mail`: String, telephone: String, companyName: String)
  /** GetResult implicit for fetching CompaniesRow objects using plain SQL queries */
  implicit def GetResultCompaniesRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]]): GR[CompaniesRow] = GR{
    prs => import prs._
    CompaniesRow.tupled((<<[Int], <<[String], <<?[String], <<[String], <<[String], <<[String], <<[String]))
  }
  /** Table description of table companies. Objects of this class serve as prototypes for rows in queries. */
  class Companies(_tableTag: Tag) extends Table[CompaniesRow](_tableTag, "companies") {
    def * = (companyId, idNumber, directorName, bankAccount, `e-mail`, telephone, companyName) <> (CompaniesRow.tupled, CompaniesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(companyId), Rep.Some(idNumber), directorName, Rep.Some(bankAccount), Rep.Some(`e-mail`), Rep.Some(telephone), Rep.Some(companyName)).shaped.<>({r=>import r._; _1.map(_=> CompaniesRow.tupled((_1.get, _2.get, _3, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column company_id SqlType(serial), AutoInc, PrimaryKey */
    val companyId: Rep[Int] = column[Int]("company_id", O.AutoInc, O.PrimaryKey)
    /** Database column id_number SqlType(varchar), Length(50,true) */
    val idNumber: Rep[String] = column[String]("id_number", O.Length(50,varying=true))
    /** Database column director_name SqlType(varchar), Length(256,true), Default(None) */
    val directorName: Rep[Option[String]] = column[Option[String]]("director_name", O.Length(256,varying=true), O.Default(None))
    /** Database column bank_account SqlType(varchar), Length(20,true) */
    val bankAccount: Rep[String] = column[String]("bank_account", O.Length(20,varying=true))
    /** Database column e-mail SqlType(varchar), Length(256,true) */
    val `e-mail`: Rep[String] = column[String]("e-mail", O.Length(256,varying=true))
    /** Database column telephone SqlType(varchar), Length(50,true) */
    val telephone: Rep[String] = column[String]("telephone", O.Length(50,varying=true))
    /** Database column company_name SqlType(varchar), Length(255,true) */
    val companyName: Rep[String] = column[String]("company_name", O.Length(255,varying=true))
  }
  /** Collection-like TableQuery object for table Companies */
  lazy val Companies = new TableQuery(tag => new Companies(tag))

  /** Entity class storing rows of table Contracts
   *  @param contractId Database column contract_id SqlType(serial), AutoInc, PrimaryKey
   *  @param contractNumber Database column contract_number SqlType(int4)
   *  @param commodityId Database column commodity_id SqlType(int4)
   *  @param consumerId Database column consumer_id SqlType(int4)
   *  @param producerId Database column producer_id SqlType(int4) */
  case class ContractsRow(contractId: Int, contractNumber: Int, commodityId: Int, consumerId: Int, producerId: Int)
  /** GetResult implicit for fetching ContractsRow objects using plain SQL queries */
  implicit def GetResultContractsRow(implicit e0: GR[Int]): GR[ContractsRow] = GR{
    prs => import prs._
    ContractsRow.tupled((<<[Int], <<[Int], <<[Int], <<[Int], <<[Int]))
  }
  /** Table description of table contracts. Objects of this class serve as prototypes for rows in queries. */
  class Contracts(_tableTag: Tag) extends Table[ContractsRow](_tableTag, "contracts") {
    def * = (contractId, contractNumber, commodityId, consumerId, producerId) <> (ContractsRow.tupled, ContractsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(contractId), Rep.Some(contractNumber), Rep.Some(commodityId), Rep.Some(consumerId), Rep.Some(producerId)).shaped.<>({r=>import r._; _1.map(_=> ContractsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column contract_id SqlType(serial), AutoInc, PrimaryKey */
    val contractId: Rep[Int] = column[Int]("contract_id", O.AutoInc, O.PrimaryKey)
    /** Database column contract_number SqlType(int4) */
    val contractNumber: Rep[Int] = column[Int]("contract_number")
    /** Database column commodity_id SqlType(int4) */
    val commodityId: Rep[Int] = column[Int]("commodity_id")
    /** Database column consumer_id SqlType(int4) */
    val consumerId: Rep[Int] = column[Int]("consumer_id")
    /** Database column producer_id SqlType(int4) */
    val producerId: Rep[Int] = column[Int]("producer_id")

    /** Foreign key referencing Commodities (database name contracts_commodities_commodity_id_fk) */
    lazy val commoditiesFk = foreignKey("contracts_commodities_commodity_id_fk", commodityId, Commodities)(r => r.commodityId, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Companies (database name contracts_companies_company_id_fk) */
    lazy val companiesFk2 = foreignKey("contracts_companies_company_id_fk", producerId, Companies)(r => r.companyId, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Companies (database name contracts_companies_company_id_fk_consumer) */
    lazy val companiesFk3 = foreignKey("contracts_companies_company_id_fk_consumer", consumerId, Companies)(r => r.companyId, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Contracts */
  lazy val Contracts = new TableQuery(tag => new Contracts(tag))

  /** Entity class storing rows of table MonitoringNews
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param chatId Database column chat_id SqlType(int4)
   *  @param keywords Database column keywords SqlType(text)
   *  @param active Database column active SqlType(bool) */
  case class MonitoringNewsRow(id: Int, chatId: Int, keywords: String, active: Boolean)
  /** GetResult implicit for fetching MonitoringNewsRow objects using plain SQL queries */
  implicit def GetResultMonitoringNewsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Boolean]): GR[MonitoringNewsRow] = GR{
    prs => import prs._
    MonitoringNewsRow.tupled((<<[Int], <<[Int], <<[String], <<[Boolean]))
  }
  /** Table description of table monitoring_news. Objects of this class serve as prototypes for rows in queries. */
  class MonitoringNews(_tableTag: Tag) extends Table[MonitoringNewsRow](_tableTag, "monitoring_news") {
    def * = (id, chatId, keywords, active) <> (MonitoringNewsRow.tupled, MonitoringNewsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(chatId), Rep.Some(keywords), Rep.Some(active)).shaped.<>({r=>import r._; _1.map(_=> MonitoringNewsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column chat_id SqlType(int4) */
    val chatId: Rep[Int] = column[Int]("chat_id")
    /** Database column keywords SqlType(text) */
    val keywords: Rep[String] = column[String]("keywords")
    /** Database column active SqlType(bool) */
    val active: Rep[Boolean] = column[Boolean]("active")
  }
  /** Collection-like TableQuery object for table MonitoringNews */
  lazy val MonitoringNews = new TableQuery(tag => new MonitoringNews(tag))

  /** Entity class storing rows of table TgAddresses
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param chatId Database column chat_id SqlType(int4), Default(None)
   *  @param lang Database column lang SqlType(varchar), Length(10,true), Default(None)
   *  @param partnerDbId Database column partner_db_id SqlType(int4), Default(None)
   *  @param username Database column username SqlType(varchar), Length(255,true), Default(None) */
  case class TgAddressesRow(id: Int, chatId: Option[Int] = None, lang: Option[String] = None, partnerDbId: Option[Int] = None, username: Option[String] = None)
  /** GetResult implicit for fetching TgAddressesRow objects using plain SQL queries */
  implicit def GetResultTgAddressesRow(implicit e0: GR[Int], e1: GR[Option[Int]], e2: GR[Option[String]]): GR[TgAddressesRow] = GR{
    prs => import prs._
    TgAddressesRow.tupled((<<[Int], <<?[Int], <<?[String], <<?[Int], <<?[String]))
  }
  /** Table description of table tg_addresses. Objects of this class serve as prototypes for rows in queries. */
  class TgAddresses(_tableTag: Tag) extends Table[TgAddressesRow](_tableTag, "tg_addresses") {
    def * = (id, chatId, lang, partnerDbId, username) <> (TgAddressesRow.tupled, TgAddressesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), chatId, lang, partnerDbId, username).shaped.<>({r=>import r._; _1.map(_=> TgAddressesRow.tupled((_1.get, _2, _3, _4, _5)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column chat_id SqlType(int4), Default(None) */
    val chatId: Rep[Option[Int]] = column[Option[Int]]("chat_id", O.Default(None))
    /** Database column lang SqlType(varchar), Length(10,true), Default(None) */
    val lang: Rep[Option[String]] = column[Option[String]]("lang", O.Length(10,varying=true), O.Default(None))
    /** Database column partner_db_id SqlType(int4), Default(None) */
    val partnerDbId: Rep[Option[Int]] = column[Option[Int]]("partner_db_id", O.Default(None))
    /** Database column username SqlType(varchar), Length(255,true), Default(None) */
    val username: Rep[Option[String]] = column[Option[String]]("username", O.Length(255,varying=true), O.Default(None))

    /** Foreign key referencing Companies (database name tg_addresses_companies_fk) */
    lazy val companiesFk = foreignKey("tg_addresses_companies_fk", partnerDbId, Companies)(r => Rep.Some(r.companyId), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TgAddresses */
  lazy val TgAddresses = new TableQuery(tag => new TgAddresses(tag))

  /** Entity class storing rows of table UsersChats
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param chatId Database column chat_id SqlType(int4)
   *  @param username Database column username SqlType(varchar), Length(255,true)
   *  @param telephone Database column telephone SqlType(varchar), Length(15,true)
   *  @param contragentId Database column contragent_id SqlType(int4), Default(None)
   *  @param firstName Database column first_name SqlType(varchar), Length(50,true)
   *  @param lastName Database column last_name SqlType(varchar), Length(50,true), Default(None)
   *  @param dateRegistred Database column date_registred SqlType(date) */
  case class UsersChatsRow(id: Int, chatId: Int, username: String, telephone: String, contragentId: Option[Int] = None, firstName: String, lastName: Option[String] = None, dateRegistred: java.sql.Date)
  /** GetResult implicit for fetching UsersChatsRow objects using plain SQL queries */
  implicit def GetResultUsersChatsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[Int]], e3: GR[Option[String]], e4: GR[java.sql.Date]): GR[UsersChatsRow] = GR{
    prs => import prs._
    UsersChatsRow.tupled((<<[Int], <<[Int], <<[String], <<[String], <<?[Int], <<[String], <<?[String], <<[java.sql.Date]))
  }
  /** Table description of table users_chats. Objects of this class serve as prototypes for rows in queries. */
  class UsersChats(_tableTag: Tag) extends Table[UsersChatsRow](_tableTag, "users_chats") {
    def * = (id, chatId, username, telephone, contragentId, firstName, lastName, dateRegistred) <> (UsersChatsRow.tupled, UsersChatsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(chatId), Rep.Some(username), Rep.Some(telephone), contragentId, Rep.Some(firstName), lastName, Rep.Some(dateRegistred)).shaped.<>({r=>import r._; _1.map(_=> UsersChatsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get, _7, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column chat_id SqlType(int4) */
    val chatId: Rep[Int] = column[Int]("chat_id")
    /** Database column username SqlType(varchar), Length(255,true) */
    val username: Rep[String] = column[String]("username", O.Length(255,varying=true))
    /** Database column telephone SqlType(varchar), Length(15,true) */
    val telephone: Rep[String] = column[String]("telephone", O.Length(15,varying=true))
    /** Database column contragent_id SqlType(int4), Default(None) */
    val contragentId: Rep[Option[Int]] = column[Option[Int]]("contragent_id", O.Default(None))
    /** Database column first_name SqlType(varchar), Length(50,true) */
    val firstName: Rep[String] = column[String]("first_name", O.Length(50,varying=true))
    /** Database column last_name SqlType(varchar), Length(50,true), Default(None) */
    val lastName: Rep[Option[String]] = column[Option[String]]("last_name", O.Length(50,varying=true), O.Default(None))
    /** Database column date_registred SqlType(date) */
    val dateRegistred: Rep[java.sql.Date] = column[java.sql.Date]("date_registred")
  }
  /** Collection-like TableQuery object for table UsersChats */
  lazy val UsersChats = new TableQuery(tag => new UsersChats(tag))
}
