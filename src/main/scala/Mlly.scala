/**
 * Created by existentialtype on 5/2/14.
 */

import scala.collection.immutable
import scala.Some
import scalaz._
import scalaz.concurrent.MVar
import scalaz.concurrent.MVar._
import scalaz.concurrent.Strategy
import scalaz.effect._
import Scalaz._
import scalaz._
import scalaz.Foldable._
import scalaz.Free._
import IO._
import scalaz.concurrent._

object Mlly {
  type Commit = MVar[Boolean]
  type Decision = MVar[Option[Commit]]
  type Candidate = MVar[Decision]
  type In = MVar[Candidate]
  type Out = MVar[Candidate]
  type Point = MVar[Unit]
  type Name = MVar[List[Point]]
  type Abort = MVar[Pair[List[Point], IO[Unit]]]
  type Synchronizer = MVar[Pair[Point, Decision]]

  type Event[T] = Synchronizer => Abort => Name => IO[T]
  type Channel[T] = (In, Out, MVar[T])

  def fix[A](f: (A => A)): A = {
    lazy val x: A = (f(x))
    x
  }

  def maybe[A, B](b: => B)(f: A => B)(opt: Option[A]): B = {
    opt.map(f).getOrElse(b)
  }

  def forkIO(f: => IO[Unit])(implicit s: Strategy): IO[Unit] =
    IO {
      (s(f.unsafePerformIO));
      ()
    }

  def atchannel(i: In, o: Out): IO[Unit] =
    for (
      ei <- i.take;
      eo <- o.take;
      si <- newEmptyMVar[Option[Commit]];
      yy <- ei.put(si);

     ki <- si take;
    so <- newEmptyMVar[Option[Commit]];
    xx <- eo.put(so);
    ko <- so take;
     _ <- maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {()})(ki)

    ) yield {

       maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {})(ki);
       maybe(ioUnit)((ci: Commit) => for (_ <- ci.put(ko isDefined)) yield {})(ki)
    }

  def atsync_help(r: Synchronizer)(a: Abort)(pair: Pair[Point, Decision]): IO[Unit] =
    fix(((z: IO[Unit]) => for (pair3 <- a take;
                               _ <- forkIO(z);
                               _ <- if (pair3._1.contains(pair._1)) ioUnit
                               else pair3._2) yield ()))

  def atsync(r: Synchronizer)(a: Abort)(x: IO[Unit]):IO[Unit] =
    for (
      pair <- r take;
      _ <- forkIO(fix((z: IO[Unit]) => for (pair2 <- r take;
                                            _ <- forkIO(z);
                                            _ <- pair2._2 put (None))
      yield ()));
      c <- newEmptyMVar[Boolean];
      _ <- pair._2 put (Some(c));
      b <- c take;
      _ <- if (b) {
        for (_ <- pair._1 put {};
             _ <- atsync_help(r)(a)(pair)

        ) yield {}
      }
      else {
        x
      }

    )
    yield ()

  def atpoint[T](sync: Synchronizer, p: Point, i: In, io: IO[T]): IO[T] =
    for (
      e <- newEmptyMVar[Decision];
      _ <- i.put(e);
      s <- e take;
      _ <- sync.put(Pair(p, s));
      _ <- p take;
      x <- io
    ) yield {
      x
    }

  def spawn(f: => IO[Unit]) = forkIO(f)

  def new_channel[T](): IO[Channel[T]] = for (
    i <- newEmptyMVar[Candidate];
    o <- newEmptyMVar[Candidate];
    _ <- forkIO(fix((z: IO[Unit]) => for (_ <- atchannel(i, o);
                                          x <- z
    ) yield {
      x
    }));
    m <- newEmptyMVar[T]


  ) yield {
    (i, o, m)
  }



  //) yield{{}}





  def receive[T](in: In, out: Out, m: MVar[T]): Event[T] =
    (s: Synchronizer) => (a: Abort) => (n: Name) =>
      for (
        t <- newEmptyMVar[Unit];
        _ <- forkIO(n.put(immutable.List(t)));
        _ <- atpoint(s, t, in, (m take));
        x <- m take
      ) yield {
        x
      }


  def transmit[T](in: In, out: Out, m: MVar[T])(b: T): Event[Unit] =
    (s: Synchronizer) => (a: Abort) => (n: Name) =>
      for (
        t <- newEmptyMVar[Unit];
        _ <- forkIO(n.put(immutable.List(t)));
        _ <- atpoint(s, t, out, (m.put(b)))
      ) yield {}


  def wrap[T, K](event: Event[T])(f: T => IO[K]): Event[K] =
    (s: Synchronizer) => (a: Abort) => (n: Name) =>
      for (
        x <- event(s)(a)(n);
        y <- f(x)
      ) yield {
        y
      }

  def choose_helper[T](vl: List[Event[T]])(j: MVar[T])(r: Synchronizer)(a: Abort)(n: Name): IO[List[Point]] = {
    vl.foldLeftM[IO, List[Point]](List[Point]())((tl_in: List[Point], v: Event[T]) =>
      for (n_new <- newEmptyMVar[List[Point]];
           _ <- forkIO(for (x <- v(r)(a)(n_new);
                            _ <- j.put(x)
           ) yield {});
           tl_new <- n_new.take;
           _ <- n_new.put(tl_new)) yield {
        tl_new ++ tl_in
      })

  }

  /*
  vl.foldLeftM[IO, Event[Unit]](List[Point]()) ((tl_in: List[Point], v: Event[Unit]) =>
  for (n_new <- newEmptyMVar[List[Point]];
         _ <- forkIO(for (x <- v(r)(a)(n_new);
                          _ <- j.put(x)
         ) yield {});
         tl_new <- n_new.take;
         _<-n_new.put(tl_new)) yield {tl_new++tl_in})
*/

  def choose[T](vl: List[Event[T]]): Event[T] = {
    (r: Synchronizer) => (a: Abort) => (n: Name) => {
      for (j <- newEmptyMVar[T];
           tl <- choose_helper[T](vl)(j)(r)(a)(n);
           _ <- forkIO(n.put(tl));
           tk <- j.take) yield {
        tk
      }
    }
  }

  def guard[T](vs: IO[Event[T]]): Event[T] =
    (s: Synchronizer) => (a: Abort) => (n: Name) =>
      for (
        v <- vs;
        x <- v(s)(a)(n)
      ) yield {
        x
      }


  def wrapabort[T](vs:IO[Unit], event:Event[T]):Event[T] =
    (s:Synchronizer) => (a:Abort) => (n:Name) =>
      for(
        _ <- forkIO(
          for(
            tL <- n.take;
            _ <- n.put(tL);
            _ <- a.put(Pair(tL, vs))
          )yield{}
        );
        x <- event (s) (a) (n)
      )yield{x}


  def sync_helper[T](v: Event[T])(j: MVar[T]): IO[Unit] =
   forkIO(fix((z: IO[Unit]) => for (
      r <- newEmptyMVar[Pair[Point, Decision]];
      a <- newEmptyMVar[Pair[List[Point], IO[Unit]]];
      n <- newEmptyMVar[List[Point]];
      _<-forkIO(atsync(r)(a)(z));
      x <- v(r)(a)(n);
      jk <- j.put(x)
    ) yield (jk)

    ))



  def sync[T](v:Event[T]):IO[T]={
    for(j<-newEmptyMVar[T];
        _<-sync_helper(v)(j);
        tk<-j.take
    ) yield {tk}
  }
}














