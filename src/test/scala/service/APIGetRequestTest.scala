package service

import testutils.UnitSpec

class APIGetRequestTest extends UnitSpec {
  case class A(a: String, b: B)
  case class B(s: String, i: Int, double: Double)
  case class C()
  case class D(a: A, c: C, json_field: String)
  private val api = new APIParamParseable {}

  "requestFieldsString" should "return an empty string for case class with no args" in {
    api.requestFieldsString[C] shouldEqual ""
  }

  "requestFieldsString" should "return a valid string of fields for flat case class" in {
    api.requestFieldsString[B] shouldEqual "s,i,double"
  }

  "requestFieldsString" should "return a valid string of fields for case class with another nested case class" in {
    api.requestFieldsString[A] shouldEqual "a,b(s,i,double)"
  }

  "requestFieldsString" should "return a valid string of fields for many nested case classes" in {
    api.requestFieldsString[D] shouldEqual "a(a,b(s,i,double)),c,json_field"
  }


}
