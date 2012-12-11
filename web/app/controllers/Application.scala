package leon.web
package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.Writes._

import examples._
import models.LeonConsole

object Application extends Controller {

  val examples = VerificationExamples.allExamples

  def index = Action { implicit request =>
    val prefix = Play.current.configuration.getString("app.prefix").getOrElse("")
    val wsURL  = Play.current.configuration.getString("app.wsURL").getOrElse(routes.Application.openConsole().webSocketURL())

    Ok(views.html.index(examples, VerificationExamples.default, prefix, wsURL))
  }

  def getExample(id: Int) = Action { 
    examples.lift.apply(id) match {
      case Some(ex) =>
        Ok(toJson(Map("status" -> "success", "code" -> ex.code)))
      case None =>
        Ok(toJson(Map("status" -> "error", "errormsg" -> "Unknown example")))
    }
  }

  def openConsole() = WebSocket.async[JsValue] { request =>
    LeonConsole.open
  }

}
