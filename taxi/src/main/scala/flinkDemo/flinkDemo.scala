package flinkDemo

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time

object flinkDemo {

  case class Location(time:Long, license:String, location:Double)

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val source:DataStream[String] = env.readTextFile("./data/")
    val stream = source.map(value => {
      val columns = value.split(",")
      Location(columns(0).toLong, columns(1), columns(2).toDouble)
    })

    val withTimestampsAndWatermarks = stream.assignAscendingTimestamps( _.time )
      .map(t => (t.license, t.location, 1))
      .keyBy(0)
      .window(SlidingEventTimeWindows.of(Time.minutes(60), Time.minutes(30)))
      .reduce()
    withTimestampsAndWatermarks.print
    env.execute("test")
  }

}
