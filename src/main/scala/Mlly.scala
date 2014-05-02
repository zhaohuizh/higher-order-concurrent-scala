/**
 * Created by existentialtype on 5/2/14.
 */

import scalaz.concurrent.MVar
import scalaz.concurrent.MVar._
import scalaz.effect._
import IO._
import scalaz.OptionT
import scalaz.ListT


type Commit=MVar[Boolean]
type Decision=MVar[Option[Commit]]
type Candidate= MVar[Option[Decision]]
type In[T]= MVar[Pair[Candidate,T=>Boolean]]
type Out[T]=MVar[Pair[Candidate,T]]
type Point= MVar[Unit]
type Name= MVar[List[Point]]
type Abort=MVar[Pair[List[Point],IO[Unit]]]
type Synchronizer =MVar[Pair[Point,Decision]]

type Event[T]=Synchronizer=>Abort=>Name=>IO[T]

def atchannel[T](i:In[T],o:Out[T])=for(
  (ei:Candidate,patt:(T=>Boolean))<- i take;
  (eo, y:T) <- o take
) yield {if (patt.apply(y)) for (  (si:Decision) <-newEmptyMVar;
                                    _<-ei put Some(si);
                                    ki <-si take;
                                    (so:Decision)<-newEmptyMVar;
                                    _ <- eo put(Some(so));
                                    ko <- newEmptyMVar;

                            )
        }




