/**
 * Created by existentialtype on 5/2/14.
 */

import scalaz.concurrent.MVar
import scalaz.concurrent.MVar._
import scalaz.effect._
import IO._
import scalaz.OptionT
import scalaz.ListT

object MyConcMl{
  type Commit = MVar[Boolean]
  type Decision = MVar[Option[Commit]]
  type Candidate = MVar[Decision]
  type In = MVar[Candidate]
  type Out= MVar[Candidate]
  type Point = MVar[Unit]
  type Name = MVar[Point]
  type Abort = MVar[Pair[List[Point], IO[Unit]]]
  type Synchronizer = MVar[Pair[Point, Decision]]

  type Event[T] = Synchronizer => Abort => Name => IO[T]

  def maybe[A, B](b: => B)(f: A => B)(opt: Option[A]): B = {
    opt.map(f).getOrElse(b)
  }

  def atchannel(i: In, o: Out): IO[Unit] = for {
    (ei: Candidate) <- i take;
    (eo: Candidate) <- o take;
    ( si: Decision) <- newEmptyMVar[Option[Commit]];
    _ <- ei.put(si)
    ki: Option[Commit] <- si take;
      (so: Decision) <- newEmptyMVar[Option[Commit]]
      _ <- eo put (so)
      ko: Option[Commit] <- so take
  }
    yield {
      maybe(ioUnit)((ci: Commit) => for {_ <- ci.put(ko isDefined)} yield {})(ki)
      maybe(ioUnit)((ci: Commit) => for {_ <- ci.put(ko isDefined)} yield {})(ki)
    }




  def atsync(r: Synchronizer)(a: Abort)(x: IO[Unit]): IO[Unit] =
    for {(t, s) <- r take


    } yield {}


  



  
def atpoint[T](sync:Synchronizer, p:Point, i: In, io: IO[T]):IO[T] = 
  for{
    (e:Decision) <-newEmptyMVar[Option[Commit]];
    _ <- i.put(e)
    s:Option <- e take;
    _ <- r.put(p s)
    _ <- p take;

  }yield {io}

}





