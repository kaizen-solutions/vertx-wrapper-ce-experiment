package io.kaizensolutions.tusk

import cats.effect.Async
import cats.syntax.all.*
import io.vertx.sqlclient.Transaction as VertxTransaction

opaque type Transaction = VertxTransaction
object Transaction:
  def from(txn: VertxTransaction): Transaction = txn

extension [F[_]](using A: Async[F])(txn: Transaction)
  def commit: F[Unit] = fromVertx(txn.commit()).void

  def rollback: F[Unit] = fromVertx(txn.rollback()).void

  def completionStatus: F[TransactionCompletionStatus] =
    fromVertx(txn.completion()).attempt.map:
      case Right(_) => TransactionCompletionStatus.Committed
      case Left(_)  => TransactionCompletionStatus.RolledBack

extension (txn: Transaction) def escapeHatch: VertxTransaction = txn

enum TransactionCompletionStatus:
  case Committed, RolledBack
