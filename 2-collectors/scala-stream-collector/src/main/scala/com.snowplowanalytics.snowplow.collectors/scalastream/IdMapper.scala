package com.snowplowanalytics.snowplow.collectors.scalastream

/**
 * Created by safouane on 05/10/15.
 */

import java.io.{FileWriter, BufferedWriter, File}
import spray.http.DateTime

/**
 * Created by safouane on 05/10/15.
 */
object IdMapper{
  type LineMap =(String,String,String)
  val maxRecord: Int = 5
  var buffer :List[Array[Byte]] = Nil


  def addToBuffer( line : Array[Byte]): List[Array[Byte]] ={
    println("Le buffer a une taille de" + buffer.length)
    if(buffer.length<maxRecord){
      buffer=line::buffer
      println(line +" viens d etre ajouté au buffer")
    }
    else{
      println("Le buffer sera commité vers S3")
      buffer = List()
      //bufferToS3()
    }
    buffer
  }

  private def bufferToS3(): Unit ={
    val timestamp: Long = System.currentTimeMillis
    val dateTime = DateTime(timestamp).toString()
    val fileToUpload = new File(dateTime)
    val bw = new BufferedWriter(new FileWriter(fileToUpload))
    buffer.foreach(a => bw.write(a+"\n"))
    bw.close()
    AmazonS3FileUpload.putFile(None,dateTime)
    buffer = List()

  }









}
