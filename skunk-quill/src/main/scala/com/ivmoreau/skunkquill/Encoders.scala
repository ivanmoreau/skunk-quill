/*
 * Copyright (C) zio-quill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivmoreau.skunkquill

import java.time._
import java.util.Date

trait Encoders[F[_]] {
  this: SkunkContext[_, F] =>

  type Encoder[T] = SkunkEncoder[T]

  type ResultRow = SkunkConnection.Row
  type Session = Unit
  type PrepareRow = Seq[Any]

  type EncoderSqlType = SqlTypes.SqlTypes
  type DecoderSqlType = skunk.data.Type

  case class SkunkEncoder[T](sqlType: EncoderSqlType)(implicit
      encoder: BaseEncoder[T]
  ) extends BaseEncoder[T] {
    override def apply(
        index: Index,
        value: T,
        row: PrepareRow,
        session: Session
    ) =
      encoder.apply(index, value, row, session)
  }

  def encoder[T](sqlType: EncoderSqlType): Encoder[T] =
    encoder(identity[T], sqlType)

  def encoder[T](f: T => Any, sqlType: EncoderSqlType): Encoder[T] =
    SkunkEncoder[T](sqlType)(new BaseEncoder[T] {
      def apply(index: Index, value: T, row: PrepareRow, session: Session) =
        row :+ f(value)
    })

  implicit def mappedEncoder[I, O](implicit
      mapped: MappedEncoding[I, O],
      e: Encoder[O]
  ): Encoder[I] =
    SkunkEncoder(e.sqlType)(new BaseEncoder[I] {
      def apply(index: Index, value: I, row: PrepareRow, session: Session) =
        e(index, mapped.f(value), row, session)
    })

  implicit def optionEncoder[T](implicit d: Encoder[T]): Encoder[Option[T]] =
    SkunkEncoder(d.sqlType)(new BaseEncoder[Option[T]] {
      def apply(
          index: Index,
          value: Option[T],
          row: PrepareRow,
          session: Session
      ) =
        value match {
          case None    => nullEncoder(index, null, row, session)
          case Some(v) => d(index, v, row, session)
        }
    })

  private[this] val nullEncoder: Encoder[Null] = encoder[Null](SqlTypes.NULL)

  implicit val stringEncoder: Encoder[String] =
    encoder[String](SqlTypes.VARCHAR)
  implicit val bigDecimalEncoder: Encoder[BigDecimal] =
    encoder[BigDecimal]((bd: BigDecimal) => bd.bigDecimal, SqlTypes.REAL)
  implicit val booleanEncoder: Encoder[Boolean] =
    encoder[Boolean](SqlTypes.BOOLEAN)
  implicit val byteEncoder: Encoder[Byte] = encoder[Byte](SqlTypes.TINYINT)
  implicit val shortEncoder: Encoder[Short] = encoder[Short](SqlTypes.SMALLINT)
  implicit val intEncoder: Encoder[Int] = encoder[Int](SqlTypes.INTEGER)
  implicit val longEncoder: Encoder[Long] = encoder[Long](SqlTypes.BIGINT)
  implicit val floatEncoder: Encoder[Float] = encoder[Float](SqlTypes.FLOAT)
  implicit val doubleEncoder: Encoder[Double] = encoder[Double](SqlTypes.DOUBLE)
  implicit val byteArrayEncoder: Encoder[Array[Byte]] =
    encoder[Array[Byte]](SqlTypes.VARBINARY)
  implicit val dateEncoder: Encoder[Date] = encoder[Date](
    (date: Date) => {
      OffsetDateTime.ofInstant(date.toInstant, dateTimeZone).toLocalDateTime
    },
    SqlTypes.DATE
  )
  implicit val localDateEncoder: Encoder[LocalDate] =
    encoder[LocalDate](SqlTypes.DATE)
  implicit val localDateTimeEncoder: Encoder[LocalDateTime] =
    encoder[LocalDateTime](SqlTypes.TIMESTAMP)
}
