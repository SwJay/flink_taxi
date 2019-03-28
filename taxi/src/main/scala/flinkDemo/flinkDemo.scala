package flinkDemo

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.core.fs.FileSystem.WriteMode.OVERWRITE

import scala.collection.mutable

object flinkDemo {

  case class Location(time:Long, license:String, location:Int)

  case class ResultStats(time: Long, locationMap: mutable.HashMap[String, Int], stats: Array[Int])

  class TaxiStats extends AggregateFunction[(Long, String, Int), ResultStats, String] {
    override def createAccumulator(): ResultStats = {
      ResultStats(600000, new mutable.HashMap[String, Int](), new Array[Int](401))
    }

    override def add(value: (Long, String, Int), accumulator: ResultStats): ResultStats = {
      var time = accumulator.time
      if(value._1 >= accumulator.time || !accumulator.locationMap.contains(value._2) || value._3 != accumulator.locationMap(value._2) ){
        accumulator.locationMap.update(value._2, value._3);
        accumulator.stats(value._3) += 1;
        if(value._1 >= accumulator.time){
          time += 600000
        }
      }
      ResultStats(time, accumulator.locationMap, accumulator.stats)
    }

    override def getResult(accumulator: ResultStats):String = {
      val current = new Array[Int](401)
      val result = new mutable.StringBuilder("grid,current,average\n")
      for(tuple <- accumulator.locationMap){
        current(tuple._2) += 1
      }
      for (i <- 1 to 400){
        result.append("%d,%d,%.2f\n".format(i, current(i), accumulator.stats(i) / 6.toDouble))
      }
      result.toString()
    }

    override def merge(a: ResultStats, b: ResultStats): ResultStats ={
      val time = Math.max(a.time, b.time)
      for(tuple <- b.locationMap){
        a.locationMap.update(tuple._1, tuple._2)
        a.stats(tuple._2) += 1
      }
      ResultStats(time, a.locationMap, a.stats)
    }
  }

  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    val source: DataStream[String] = env.readTextFile("../data/data4/out")

    val stream = source.map(value => {
      val columns = value.split(",")
      Location(columns(0).toLong * 1000, columns(1), columns(2).toInt)
    })


    val resultStream = stream.assignAscendingTimestamps( _.time )
      .map(t => (t.time ,t.license, t.location))
      .windowAll(SlidingEventTimeWindows.of(Time.minutes(60), Time.minutes(30)))
      .aggregate(new TaxiStats)


    resultStream.writeAsText("./result/4", OVERWRITE)
    env.execute("flinkSucks")
  }

}
