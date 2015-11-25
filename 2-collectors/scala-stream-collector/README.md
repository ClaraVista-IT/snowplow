This is a snowplow fork to customize the Scala collector 
The Scala collector is able to 
	Work in both HTTP and HTTPS in a stand alone mode (Without LoadBalancer)
		All SSL configurations are writen in a the global configuration file

	
	To redirect requests to an ID-matching plateform as AppNexus, make some matching id, and flush a buffer to a Kinesis Stream
	       All must be found in the global configuration file
