package scala.slick.jdbc

import java.sql.ResultSet
import scala.slick.action.{Effect, NoStream, SynchronousDatabaseAction}
import scala.slick.profile.BasicStreamingAction
import scala.slick.util.CloseableIterator

/** An invoker which calls a function to retrieve a ResultSet. This can be used
  * for reading information from a java.sql.DatabaseMetaData object which has
  * many methods that return ResultSets.
  *
  * For convenience, if the function returns null, this is treated like an
  * empty ResultSet. */
abstract class ResultSetInvoker[+R] extends Invoker[R] { self =>

  protected def createResultSet(session: JdbcBackend#Session): ResultSet

  def iteratorTo(maxRows: Int)(implicit session: JdbcBackend#Session): CloseableIterator[R] = {
    val rs = createResultSet(session)
    if(rs eq null) CloseableIterator.empty
    else {
      val pr = new PositionedResult(rs) {
        def close() = rs.close()
      }
      new PositionedResultIterator[R](pr, maxRows) {
        def extractValue(pr: PositionedResult) = self.extractValue(pr)
      }
    }
  }

  protected def extractValue(pr: PositionedResult): R
}

object ResultSetInvoker {
  def apply[R](f: JdbcBackend#Session => ResultSet)(implicit conv: PositionedResult => R): Invoker[R] = new ResultSetInvoker[R] {
    def createResultSet(session: JdbcBackend#Session) = f(session)
    def extractValue(pr: PositionedResult) = conv(pr)
  }
}

object ResultSetAction {
  def apply[R](f: JdbcBackend#Session => ResultSet)(implicit conv: PositionedResult => R): BasicStreamingAction[Effect.Read, Vector[R], R] = new StreamingInvokerAction[Effect.Read, Vector[R], R] {
    protected[this] def createInvoker(sql: Iterable[String]) = ResultSetInvoker(f)(conv)
    protected[this] def createBuilder = Vector.newBuilder[R]
    def statements = Nil
  }
}
