/**
 * Created by existentialtype on 5/2/14.
 */

import scalaz.concurrent.MVar
import scalaz.concurrent.MVar._
import scalaz.concurrent.Strategy
import scalaz.effect._
import IO._
import concurrent._
import scalaz.OptionT
import scalaz.ListT

object Mlly2{
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
  type Channel[T]=(In,Out,MVar[T])

  def maybe[A, B](b: => B)(f: A => B)(opt: Option[A]): B = {
    opt.map(f).getOrElse(b)
  }

  def forkIO(f: => IO[Unit])(implicit s: Strategy): IO[Unit] =
    IO { s(f.unsafePerformIO); () }

  def atchannel(i: In, o: Out): IO[Unit] =
    for(
      ei <- i.take;
      eo <- o.take;
      si <- newEmptyMVar[Option[Commit]];
      yy <- ei.put(si);
      ki <- si take;
      so <- newEmptyMVar[Option[Commit]];
      xx <- eo.put(so);
      ko <- so take
     // _ <- maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {()})(ki)

    ) yield {

     // maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {})(ki);
      //maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {})(ki)
    }


  def atpoint[T](sync:Synchronizer, p:Point, i: In, io: IO[T]): IO[T] = 
    for(
      e <- newEmptyMVar[Decision];
      _ <- i.put(e);
      s <- e take;
      _ <- sync.put(Pair(p, s));
      _ <- p take;
      x <- io
    ) yield {x}




  def receive[T](in:In, out:Out, m:MVar[T]): Event[T] = 
    (s:Synchronizer) => (a:Abort) => (n:Name) =>
      for(
        t <- newEmptyMVar[Unit];
        _ <- forkIO(n.put(t));
        _ <- atpoint(s, t, in, (m take));
        x <- m take
      ) yield{x}


  def transmit[T](in:In, out:Out, m:MVar[T]) (b:T): Event[Unit] = 
    (s:Synchronizer) => (a:Abort) => (n:Name) =>
      for(
        t <- newEmptyMVar[Unit];
        _ <- forkIO(n.put(t));
        _ <- atpoint(s, t, out, (m.put(b)))
      ) yield{}
 
}
