package com.persona

import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import com.persona.http.account.AccountApi
import com.persona.http.authentication.AuthenticationApi
import com.persona.http.authorization.AuthorizationApi
import com.persona.http.bank.BankApi
import com.persona.http.chat.ChatApi
import com.persona.http.offer.OfferApi
import com.persona.service.account.google.{SlickGoogleAccountDAO, GoogleTokenConverter, GoogleAccountService}
import com.persona.service.account.{AccountService, AccountValidator, SlickAccountDAO}
import com.persona.service.authentication.AuthenticationService
import com.persona.service.authentication.facebook.FacebookAuthService
import com.persona.service.authentication.google.GoogleTokenValidationService
import com.persona.service.authorization.AuthorizationService
import com.persona.service.bank.{BankService, CassandraBankDAO, DataItemValidator, JsonDataSchemaLoader}
import com.persona.service.chat.ChatService
import com.persona.service.chat.dao.ChatDAO
import com.persona.service.offer.{CassandraOfferDAO, OfferService}
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend._

import scala.concurrent.ExecutionContext

class Bootstrap
  (config: Config, http: HttpExt)
  (implicit actorSystem: ActorSystem, executionContext: ExecutionContext, materializer: Materializer) {

  private[this] val personaConfig = config.getConfig("persona")
  private[this] val googleClientId = personaConfig.getString("google_client_id")

  private[this] val db = Database.forConfig("db", personaConfig)

  private[this] val googleTokenValidationService = GoogleTokenValidationService(googleClientId, http)

  private[this] val authorizationService = new AuthorizationService
  private[this] val authorizationApi = new AuthorizationApi(authorizationService)

  private[this] val accountValidator = new AccountValidator
  private[this] val passwordLogRounds = personaConfig.getInt("passwordLogRounds")
  private[this] val accountDAO = new SlickAccountDAO(db)
  private[this] val accountService = AccountService(accountDAO, passwordLogRounds)
  private[this] val googleTokenConverter = new GoogleTokenConverter
  private[this] val googleAccountDAO = new SlickGoogleAccountDAO(db)
  private[this] val googleAccountService = GoogleAccountService(googleTokenConverter, googleAccountDAO, googleTokenValidationService)
  private[this] val accountApi = new AccountApi(accountService, accountValidator, googleAccountService)

  private[this] val authenticationService = AuthenticationService(accountDAO)
  private[this] val facebookAuthService = new FacebookAuthService
  private[this] val authenticationApi = new AuthenticationApi(authenticationService, facebookAuthService)

  private[this] val bankDAO = new CassandraBankDAO()
  private[this] val dataSchemaLoader = new JsonDataSchemaLoader(personaConfig.getString("schemaDirectory"))
  private[this] val dataItemValidator = new DataItemValidator(dataSchemaLoader)
  private[this] val bankService = BankService(bankDAO, dataItemValidator)
  private[this] val bankApi = new BankApi(bankService)

  private[this] val offerDAO = new CassandraOfferDAO()
  private[this] val offerService = OfferService(offerDAO)
  private[this] val offerApi = new OfferApi(offerService)

  private[this] val chatDAO = new ChatDAO(db)
  private[this] val chatService = new ChatService(chatDAO)
  private[this] val chatApi = new ChatApi(chatService)

  val routes = {
    accountApi.route ~
    authenticationApi.route ~
    authorizationApi.route ~
    bankApi.route ~
    offerApi.route ~
    chatApi.route
  }

}
