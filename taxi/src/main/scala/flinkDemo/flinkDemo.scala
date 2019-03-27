package flinkDemo

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time

import scala.collection.mutable

object flinkDemo {

  case class Location(time:Long, license:String, location:Int)

  case class ResultStats(locationMap: mutable.HashMap[String, Int], stats: Array[Int])

  class TaxiStats extends AggregateFunction[(String, Int), ResultStats, String] {
    override def createAccumulator(): ResultStats = {
      ResultStats(new mutable.HashMap[String, Int](), new Array[Int](11))
    }

    override def add(value: (String, Int), accumulator: ResultStats): ResultStats = {
      accumulator.locationMap.update(value._1, value._2);
      accumulator.stats(value._2) += 1;
      accumulator
    }

    override def getResult(accumulator: ResultStats):String = {
      val current = new Array[Int](11)
      val result = new mutable.StringBuilder("\ngird,current,average\n")
      for(tuple <- accumulator.locationMap){
        current(tuple._2) += 1
      }
      for (i <- 1 to 10){
        result.append("%d,%d,%.2f\n".format(i, current(i), accumulator.stats(i) / 6.toDouble))
      }
      result.toString()
    }

    override def merge(a: ResultStats, b: ResultStats): ResultStats ={
      for(tuple <- b.locationMap){
        a.locationMap.update(tuple._1, tuple._2)
        a.stats(tuple._2) += 1
      }
      a
    }
  }

  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    val source: DataStream[String] = env.readTextFile("./data")

    val stream = source.map(value => {
      val columns = value.split(",")
      Location(columns(0).toLong, columns(1), columns(2).toInt)
    })


    val resultStream = stream.assignAscendingTimestamps( _.time )
      .map(t => (t.license, t.location))
      .windowAll(SlidingEventTimeWindows.of(Time.seconds(4), Time.seconds(2)))
      .aggregate(new TaxiStats)


    resultStream.print//writeAsText("./out")
    env.execute("flinkSucks")
  }

}
