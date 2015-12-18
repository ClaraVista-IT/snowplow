This is a snowplow fork to customize the Scala collector 
The Scala collector is able to 
	Work in both HTTP and HTTPS in a stand alone mode (Without LoadBalancer)
		All SSL configurations are writen in a the global configuration file

	
	To redirect requests to an ID-matching plateform as AppNexus, make some matching id, and flush a buffer to a Kinesis Stream
	       All must be found in the global configuration file
	       
	       
	       
	       
	       
	       ##                                Big Collector
This is a snowplow fork to customize the Scala collector 
The Scala collector is able to :
* Work in both HTTP and HTTPS in a stand alone mode (Without LoadBalancer): All SSL configurations are writen in a the global configuration file

	
* Redirect requests to an ID-matching plateform as AppNexus, make some matching id, and flush a buffer to a Kinesis Stream.

 All must be found in the global configuration file


### 1. Deploying the Collector :
The CollectorLaunch folder contains many scripts in order to avoid some dynamic configurations such as ip adress and certificate path.
So, clone the project, and extract all files in the CollectorLaunch folder, on the root.

### 2. Configuration : 
In this collector we've added two principal features :
##### 2.1  Working in both HTTP/HTTPS :

As a default behavior, this collector works in a http mode. To enable https we must provide one file and 3 options : 


  * A certificate file in a JKS (Java-Key-Store) format (we can test by creating a self-signed certificate)
`keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -validity 360 -keysize 2048`
  * The password of the JKS 

  * The password of the certificate
  
These options must be writen in the main configuration file **`collector5.conf`**

```     
 certificate{
file-password = "JKS_PASSWORD"
path-to-certificate = "JKS_PATH"
keystore-password = "KEYSTORE_PASSWORD"
     } 
```




##### 2.2 Making id-matching :
The id matching is made via a matching plateform.

**How it works ?**

In a classic case (without matching plateform), events are handled directly by our collector. (the traker is configured to fire events to a specific ip-adress and port).

To enable id-matching, events must transit by the plateform before being redirected to our collector. Meanwhile, the plateform injects a cookie to the origin and attach his value to the request to be sent to our collector.

So the value of the injected cookie is added as an event argument to the http request key-value.
(the key depends on the matching plateform ).

**Goal**

Our goal is to be able to handle these requests, extract the matching cookie value, extract our cookie and flush the result to a Kinesis stream.


**Configuration**

In the configuration file we must provide :

* The key of the cookie value

```
redirect{
        # Boolean value (true/false)
        allow-redirect = false
        path-to-redirect = ""
        redirect-id = ""
        }
```



 
