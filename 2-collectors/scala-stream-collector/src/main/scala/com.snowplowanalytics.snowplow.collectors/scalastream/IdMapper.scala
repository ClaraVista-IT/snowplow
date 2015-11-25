package com.snowplowanalytics.snowplow.collectors.scalastream

/**
 * Created by safouane on 05/10/15.
 */

import java.io.{BufferedWriter, File, FileWriter}

import spray.http.DateTime

/**
 * Created by safouane on 05/10/15.
 */



/**
 * Cet objet offre un buffer avec ses helpers d'ajout d'élements, et de vidage
 *
 *
 * */


object IdMapper{
  type LineMap =(String,String,String)
 // val maxRecord: Int = 450
  /**On déclare le buffer*/
  var buffer :List[Array[Byte]] = Nil



   /**Ajouter un élément au buffer*/
  def addToBuffer( line : Array[Byte]): List[Array[Byte]] ={
    println("Le buffer a une taille de" + buffer.length)
    buffer=line::buffer
    buffer
  }



  /** This method is not used , it allows storing a buffer directly to S3*/
  private def bufferToS3(): Unit ={
    val timestamp: Long = System.currentTimeMillis
    val dateTime = DateTime(timestamp).toString()
    val fileToUpload = new File(dateTime)
    val bw = new BufferedWriter(new FileWriter(fileToUpload))
    buffer.foreach(a => bw.write(a+"\n"))
    bw.close()
    AmazonS3FileUpload.putFile(None,dateTime)
    flushBuffer()
  }

  /** Vider le buffer */
 def flushBuffer(): Unit ={
   buffer = List()
 }






}
