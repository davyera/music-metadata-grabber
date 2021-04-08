package service

import io.circe.Decoder
import models._
import models.api.response._

import scala.reflect.runtime.universe._
import sttp.client.UriContext
import sttp.client.circe.asJson
import sttp.model.Uri

trait APIParamParseable {

  /** Recursively construct string of field names for a case class structure. Ex:
   *  case class A(a: String, b: B)
   *  case class B(s: String, i: Int)
   *
   *  return >> "a,b(s,i)"
   */
  def requestFieldsString[T: TypeTag]: String = {
    def fieldNames(forType: Type): String = {
      // grab case class fields
      val members = forType.decls.sorted.collect {
        case member: MethodSymbol if member.isCaseAccessor => member
      }
      // map field names and recurse through children if needed
      members.map { member =>
        val childFieldNames = fieldNames(member.returnType)
        val childrenNames = if (childFieldNames.isEmpty) "" else s"($childFieldNames)"
        s"${member.name}$childrenNames"
      }.mkString(",")
    }
    fieldNames(typeOf[T])
  }
}

abstract class APIGetRequest[R] extends APIParamParseable {
  val uri: Uri
  implicit val decoder: Decoder[R]
  lazy val baseRequest: Request[R] = sttp.client.basicRequest.get(uri).response(asJson[R])
}