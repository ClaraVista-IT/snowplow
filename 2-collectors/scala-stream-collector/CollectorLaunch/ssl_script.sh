# @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
#  By Saf
# @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
# This script reads a configuration file from the Snowplow collector. If the 'ssl-encryption' is allowed, it implements an interface to handle all SSL/TLS connections
#
#
#


if [ `grep -c 'ssl-encryption = on' collector5.conf`  == 1 ]
then
echo "SSL ENCRYPTION IS ENABLED, WE'LL  IMPLEMENT SSL INTERFACE"
sed -i -e 's/ScalaCollector extends App{/ScalaCollector extends App with CamelSslConfiguration{/g' src/main/scala/com.snowplowanalytics.snowplow.collectors/scalastream/ScalaCollectorApp.scala
KEYSTOREPASS=$( grep keystore-password collector5.conf| cut -f 2 -d '=')
FILEPASS=$(cat collector5.conf | grep file-password | cut -f 2 -d '=')
CERTPATH=$(cat collector5.conf | grep path-to-certificate | cut -f 2 -d '=')


# Finding certificate values and replace them in the CamelSslConfiguration trait

echo "KEYSTOREPASS = " $KEYSTOREPASS
echo "CERTPATH = " $CERTPATH
echo "FILEPASS = " $FILEPASS


sed  -i "s/\(val keyStoreFile *= *\).*/\1$CERTPATH/" src/main/scala/com.snowplowanalytics.snowplow.collectors/scalastream/CamelSslConfiguration.scala
sed  -i "s/\(val keyStorePassword *= *\).*/\1$KEYSTOREPASS/" src/main/scala/com.snowplowanalytics.snowplow.collectors/scalastream/CamelSslConfiguration.scala
sed  -i "s/\(val filePassword *= *\).*/\1$FILEPASS/" src/main/scala/com.snowplowanalytics.snowplow.collectors/scalastream/CamelSslConfiguration.scala


else
echo "SSL ENCRYPTION IS DISABLED, VERIFYING APP main class . . ."
sed -i -e 's/ScalaCollector extends App with CamelSslConfiguration{/ScalaCollector extends App{/g' src/main/scala/com.snowplowanalytics.snowplow.collectors/scalastream/ScalaCollectorApp.scala
fi

