import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, concat, lit}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DataType, IntegerType, StringType, StructField, StructType}
import scala.util.Try

case class Crime(
  INCIDENT_NUMBER: String,
  OFFENSE_CODE: Int,
  OFFENSE_CODE_GROUP: String,
  OFFENSE_DESCRIPTION: String,
  DISTRICT: String,
  REPORTING_AREA: String,
  SHOOTING: String,
  OCCURRED_ON_DATE: String,
  YEAR: Int,
  MONTH: Int,
  DAY_OF_WEEK: String,
  HOUR: Int,
  UCR_PART: String,
  STREET: String,
  Lat: Option[Double],
  Long: Option[Double],
  Location: Option[String]
){
  def wasShooting: Boolean = {SHOOTING != "null"}
}

object MyFirstScalaJob extends App{

  if (args.length < 2) {
    println("Error: Should be minimum two parameters, Input and Output directories")
    System.exit(1)
  }

  val spark: SparkSession = SparkSession
    .builder()
    .master("local[*]")
    .appName("MyFirstSparkJob")
    .getOrCreate()

  import spark.implicits._

  val crimeFacts = spark
    .read
    .option("header", "true")
    .option("interSchema", "true")
    .csv(args(0))

  println("crimes_total:")
  val crimes_total = crimeFacts.groupBy("DISTRICT").count()
  crimes_total.write.mode("overwrite").parquet(args(1) + "/crimes_total.parquet")

  val df = crimeFacts.withColumn("YEAR_MONTH",concat(col("YEAR"),lit(',')
    ,col("MONTH")))
    .drop("YEAR")
    .drop("MONTH")

  val df1 = df.groupBy("DISTRICT", "YEAR_MONTH").count()
  df1.createOrReplaceTempView("df1")
  println("crimes_monthly:")
  val crimes_monthly = spark.sql("select DISTRICT, percentile_approx(count, 0.5) as median from df1 group by DISTRICT order by DISTRICT")
    .withColumn("median",col("median").cast("Int"))
  Try (crimes_monthly.write.mode("overwrite").parquet(args(1) + "/crimes_monthly.parquet")).recover({
    case e: Exception => println(e); throw e;
  })

  val mostTypesCrimesCount = crimeFacts.groupBy("OFFENSE_CODE_GROUP").count()
    .sort(desc("count"))
    .take(3)

  var lst = "";
  for (e <- mostTypesCrimesCount) {
    if (lst == "")
      lst = lst + e(0).toString()
    else
      lst = lst + " , "+ e(0).toString()
  }
  println("The most 3 types of crime:")
  println(lst)

  println("average latitude in the DISTRICT:")
  val lat_average = crimeFacts.groupBy("DISTRICT").agg(avg("Lat"))
    .withColumnRenamed("avg(Lat)","avg_lat")
  lat_average.write.mode("overwrite").parquet(args(1) + "/lat_average.parquet")

  println("average longitude in the DISTRICT:")
  val long_average = crimeFacts.groupBy("DISTRICT").agg(avg("Long"))
    .withColumnRenamed("avg(Long)","avg_long")
  long_average.write.mode("overwrite").parquet(args(1) + "/long_average.parquet")
  println("Finished successful...")
}
