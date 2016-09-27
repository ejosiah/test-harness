val pattern = "(.+)://.+".r
val str = "http://local.tax.service.gov.uk/emcs/trader/GB00001234569/movement/draft/670706/consignor"

val res = str match { case pattern(x) => x }  // extraction by matching

pattern.unapplySeq(str) match {
  case Some(protocol :: _) => println(protocol)
}