/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.can

import org.specs2._
import utils.DateTime
import HttpProtocols._
import matcher.DataTables

class ResponsePreparerSpec extends Specification with ResponsePreparer with DataTables { def is =

  "The response preparation logic should properly render a response" ^
    "with status 200, no headers and no body"                        ! e1^
    "with status 304, a few headers and no body"                     ! e2^
    "with status 400, a few headers and a body"                      ! e3^
                                                                     end^
  "The 'Connection' header should be rendered correctly"             ! e4

  def e1 = prep(`HTTP/1.1`) {
    HttpResponse(200, Nil)
  } mustEqual prep {
    """|HTTP/1.1 200 OK
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |
       |""" -> false
  }

  def e2 = prep(`HTTP/1.1`) {
    HttpResponse(304, List(
      HttpHeader("Server", "spray-can/1.0"),
      HttpHeader("Age", "0")
    ))
  } mustEqual prep {
    """|HTTP/1.1 304 Not Modified
       |Age: 0
       |Server: spray-can/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |
       |""" -> false
  }

  def e3 = prep(`HTTP/1.1`) {
    HttpResponse(400, List(
      HttpHeader("Server", "spray-can/1.0"),
      HttpHeader("Cache-Control", "public")
    ), "Small f*ck up overhere!".getBytes(US_ASCII))
  } mustEqual prep {
    """|HTTP/1.1 400 Bad Request
       |Cache-Control: public
       |Server: spray-can/1.0
       |Content-Length: 23
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |
       |Small f*ck up overhere!""" -> false
  }

  val NONE: Option[String] = None
  
  def e4 =
    "Client Version" | "Request"          | "Response"         | "Rendered"         | "Close" |
    `HTTP/1.1`       ! NONE               ! NONE               ! NONE               ! false   |
    `HTTP/1.1`       ! Some("close")      ! NONE               ! Some("close")      ! true    |
    `HTTP/1.1`       ! Some("Keep-Alive") ! NONE               ! NONE               ! false   |
    `HTTP/1.0`       ! NONE               ! NONE               ! NONE               ! true    |
    `HTTP/1.0`       ! Some("close")      ! NONE               ! NONE               ! true    |
    `HTTP/1.0`       ! Some("Keep-Alive") ! NONE               ! Some("Keep-Alive") ! false   |
    `HTTP/1.1`       ! NONE               ! Some("close")      ! Some("close")      ! true    |
    `HTTP/1.0`       ! Some("close")      ! Some("Keep-Alive") ! Some("Keep-Alive") ! false   |> {
      (reqProto, reqCH, resCH, renCH, close) =>
      prep(reqProto, reqCH) {
        HttpResponse(200, resCH.map(h => List(HttpHeader("Connection", h))).getOrElse(Nil))
      } mustEqual prep {
        "HTTP/1.1 200 OK\n" +
        renCH.map("Connection: " + _ + "\n").getOrElse("") +
        "Date: Thu, 25 Aug 2011 09:10:29 GMT\n\n" -> close
      }
    }

  def prep(reqProtocol: HttpProtocol, reqConnectionHeader: Option[String] = None)(response: HttpResponse) = {
    val sb = new java.lang.StringBuilder()
    val rawResponse = prepare(response, reqProtocol, reqConnectionHeader)
    rawResponse.buffers.foreach { buf =>
      sb.append(new String(buf.array, US_ASCII))
    }
    sb.toString -> rawResponse.closeConnection
  }

  def prep(t: Tuple2[String, Boolean]) = t._1.stripMargin.replace("\n", "\r\n") -> t._2

  override val dateTimeNow = DateTime(2011, 8, 25, 9,10,29) // provide a stable date for testing

}