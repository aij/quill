package io.getquill.context.jdbc.postgres

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

import io.getquill.context.sql.encoding.ArrayEncodingBaseSpec
import io.getquill.{ Literal, PostgresJdbcContext }

class ArrayJdbcEncodingSpec extends ArrayEncodingBaseSpec {
  val ctx = testContext
  import ctx._

  val q = quote(query[ArraysTestEntity])

  "Support all sql base types and `Seq` implementers" in {
    ctx.run(q.insert(lift(e)))
    val actual = ctx.run(q).head
    actual mustEqual e
    baseEntityDeepCheck(actual, e)
  }

  "Support Seq encoding basing on MappedEncoding" in {
    val wrapQ = quote(querySchema[WrapEntity]("ArraysTestEntity"))
    ctx.run(wrapQ.insert(lift(wrapE)))
    ctx.run(wrapQ).head.texts mustBe wrapE.texts
  }

  "Timestamps" in {
    case class Timestamps(timestamps: List[Timestamp])
    val tE = Timestamps(List(new Timestamp(System.currentTimeMillis())))
    val tQ = quote(querySchema[Timestamps]("ArraysTestEntity"))
    ctx.run(tQ.insert(lift(tE)))
    ctx.run(tQ).head.timestamps mustBe tE.timestamps
  }

  "Catch invalid decoders" in {
    val newCtx = new PostgresJdbcContext[Literal]("testPostgresDB") {
      // avoid transforming from java.sql.Date to java.time.LocalDate
      override implicit def arrayLocalDateDecoder[Col <: Seq[LocalDate]](implicit bf: CBF[LocalDate, Col]): Decoder[Col] =
        arrayDecoder[LocalDate, LocalDate, Col](identity)
    }
    import newCtx._
    newCtx.run(query[ArraysTestEntity].insert(lift(e)))
    intercept[IllegalStateException] {
      newCtx.run(query[ArraysTestEntity]).head mustBe e
    }
    newCtx.close()
  }

  "Custom decoders/encoders" in {
    case class Entity(uuids: List[UUID])
    val e = Entity(List(UUID.randomUUID(), UUID.randomUUID()))
    val q = quote(querySchema[Entity]("ArraysTestEntity"))

    implicit def arrayUUIDEncoder[Col <: Seq[UUID]]: Encoder[Col] = arrayRawEncoder[UUID, Col]("uuid")
    implicit def arrayUUIDDecoder[Col <: Seq[UUID]](implicit bf: CBF[UUID, Col]): Decoder[Col] = arrayRawDecoder[UUID, Col]

    ctx.run(q.insert(lift(e)))
    ctx.run(q).head.uuids mustBe e.uuids
  }

  "Arrays in where clause" in {
    ctx.run(q.insert(lift(e)))
    val actual1 = ctx.run(q.filter(_.texts == lift(List("test"))))
    val actual2 = ctx.run(q.filter(_.texts == lift(List("test2"))))
    actual1 mustEqual List(e)
    actual2 mustEqual List()
  }

  override protected def beforeEach(): Unit = {
    ctx.run(q.delete)
    ()
  }
}
