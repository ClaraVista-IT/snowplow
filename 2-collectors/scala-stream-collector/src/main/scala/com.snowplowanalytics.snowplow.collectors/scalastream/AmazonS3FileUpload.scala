package com.snowplowanalytics.snowplow.collectors.scalastream

/**
 * Created by safouane on 05/10/15.
 */

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import java.io.File



object AmazonS3FileUpload  {


  val bucketName = "id.mapping"          // specifying bucket name

  //file to upload

  /* These Keys would be available to you in  "Security Credentials" of
      your Amazon S3 account */
  val AWS_ACCESS_KEY = ""
  val AWS_SECRET_KEY = ""

  val yourAWSCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
  val amazonS3Client = new AmazonS3Client(yourAWSCredentials)
  // This will create a bucket for storage
  //amazonS3Client.createBucket(bucketName)

  def putFile(bucketName: Option[String], fileName:String) : Boolean={
    val fileToUpload = new File(fileName)
    amazonS3Client.putObject(bucketName.getOrElse("id.mapping"), fileName, fileToUpload)
    println(fileName+ "est ajouté a S3")
    //fileToUpload.delete()

    true
  }


}