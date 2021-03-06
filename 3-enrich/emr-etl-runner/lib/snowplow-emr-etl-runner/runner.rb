# Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
#
# This program is licensed to you under the Apache License Version 2.0,
# and you may not use this file except in compliance with the Apache License Version 2.0.
# You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the Apache License Version 2.0 is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.

# Author::    Alex Dean (mailto:support@snowplowanalytics.com)
# Copyright:: Copyright (c) 2012-2014 Snowplow Analytics Ltd
# License::   Apache License Version 2.0

require 'contracts'

module Snowplow
  module EmrEtlRunner
    class Runner

      include Contracts

      # Supported options
      @@collector_format_regex = /^(?:cloudfront|clj-tomcat|thrift|(?:json\/.+\/.+)|(?:tsv\/.+\/.+))$/
      @@skip_options = Set.new(%w(staging s3distcp emr enrich shred archive))

      include Logging

      # Initialize the class.
      Contract ArgsHash, ConfigHash, ArrayOf[String] => Runner
      def initialize(args, config, enrichments_array)

        # Let's set our logging level immediately
        Logging::set_level config[:logging][:level]

        @args = args
        @config = validate_and_coalesce(args, config)
        @enrichments_array = enrichments_array
        
        self
      end

      # Our core flow
      Contract None => nil
      def run

        # Now our core flow
        unless @args[:skip].include?('staging')
          unless S3Tasks.stage_logs_for_emr(@args, @config)
            raise NoDataToProcessError, "No Snowplow logs to process since last run"
          end
        end

        unless @args[:skip].include?('emr')
          enrich = not(@args[:skip].include?('enrich'))
          shred = not(@args[:skip].include?('shred'))
          s3distcp = not(@args[:skip].include?('s3distcp'))
          job = EmrJob.new(@args[:debug], enrich, shred, s3distcp, @config, @enrichments_array)
          job.run()
        end

        unless @args[:skip].include?('archive')
          S3Tasks.archive_logs(@config)
        end

        logger.info "Completed successfully"
        nil
      end

      # Adds trailing slashes to all non-nil bucket names in the hash
      def self.add_trailing_slashes(bucketData)
        if bucketData.class == ''.class
          Sluice::Storage::trail_slash(bucketData)
        elsif bucketData.class == {}.class
          bucketData.each {|k,v| add_trailing_slashes(v)}
        elsif bucketData.class == [].class
          bucketData.each {|b| add_trailing_slashes(b)}
        end
      end

      # Validate our arguments against the configuration Hash
      # Make updates to the configuration Hash based on the
      # arguments
      Contract ArgsHash, ConfigHash => ConfigHash
      def validate_and_coalesce(args, config)

        # Check our skip argument
        args[:skip].each { |opt|
          unless @@skip_options.include?(opt)
            raise ConfigError, "Invalid option: skip can be 'staging', 'emr', 'enrich', 'shred' or 'archive', not '#{opt}'"
          end
        }

        if args[:skip].include?('shred') and args[:skip].include?('enrich') and !args[:skip].include?('emr')
          args[:skip] << 'emr'
        end

        # Check that start is before end, if both set
        if !args[:start].nil? and !args[:end].nil?
          if args[:start] > args[:end]
            raise ConfigError, "Invalid options: end date '#{_end}' is before start date '#{start}'"
          end
        end

        input_collector_format = config[:etl][:collector_format]

        # Validate the collector format
        unless input_collector_format =~ @@collector_format_regex
          raise ConfigError, "collector_format '%s' not supported" % input_collector_format
        end

        # Currently we only support start/end times for the CloudFront collector format. See #120 for details
        unless config[:etl][:collector_format] == 'cloudfront' or (args[:start].nil? and args[:end].nil?)
          raise ConfigError, "--start and --end date arguments are only supported if collector_format is 'cloudfront'"
        end

        # We can't process enrich and process shred
        unless args[:process_enrich_location].nil? or args[:process_shred_location].nil?
          raise ConfigError, "Cannot process enrich and process shred, choose one"
        end
        unless args[:process_enrich_location].nil?
          config[:s3][:buckets][:raw][:processing] = args[:process_enrich_location]
        end
        unless args[:process_shred_location].nil?
          config[:s3][:buckets][:enriched][:good] = args[:process_shred_location]
        end

        # Add trailing slashes if needed to the non-nil buckets
        config[:s3][:buckets] = Runner.add_trailing_slashes(config[:s3][:buckets])

        config
      end

    end
  end
end
