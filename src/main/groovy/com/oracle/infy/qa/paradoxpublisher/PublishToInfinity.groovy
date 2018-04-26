package com.oracle.infy.qa.paradoxpublisher

import groovyx.net.http.*
import groovy.util.logging.Log4j

/**
 * Publish results to infinity analytics
 */
@Log4j
class PublishToInfinity implements Publisher {
    static final PUBLISHER_VERSION = '0.1'
    RESTClient auto
    RESTClient scs

    static void main(String[] args) {
        new PublishToInfinity().parseCommandline(args)
    }

    def sendEvent(Map event) {
        event << [
            referrer: 'paradox',
            publisherVersion: PUBLISHER_VERSION,
        ]
        def queryString = event
            .findAll { it.value }
            .collectEntries { [it.key, URLEncoder.encode(it.value as String, 'UTF-8')] }
            .collect { "$it.key=$it.value" }.join('&')
        log.info "Sending ${config.scsUrl}?$queryString"
        scs.get(uri: "$config.scsUrl?$queryString", headers: ['User-Agent': 'InfinityParadoxPublisher'])
    }

    def publish(String assemblyName, String executionGuid) {
        if (!config.with { autoUrl && scsUrl }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        scs = new RESTClient(config.scsUrl).with { parser.'image/gif' = parser.defaultParser; it }
        log.info "Fetching results from ${auto.uri}/results/$assemblyName/$executionGuid ..."
        def results = auto.get(uri: "${auto.uri}/results/$assemblyName/$executionGuid").data
        log.debug "Results = $results"

        // Send Test Results
        log.info "Result Count = ${results.tests.size()}"
        for (test in results.tests) {
            def evt = [
                'wt.co_f': executionGuid,
                suiteName: assemblyName,
                environment: results.environment,
                testName: test.name ?: '',
                'page-uri': test.name.replaceAll('\\.', '/'),
                state: test.state,
                'client-ip': InetAddress.localHost.hostName,
                'authenticated-username': System.properties.'user.name',
                commandLine: results.commandline,
                date: results.date,
                time: results.time,
            ]
            if (test.labels) { evt << [ 'wt.cg_n': test.labels.join(';') ] }
            if (test.performance) { evt << [ performance: test.performance.toString() ] }
            if (test.defect) { evt << [ defect: test.defect ] }
            try {
                sendEvent(evt)
            } catch (HttpResponseException ex) {
                log.error ("Was unable to send event to Infinity.\ntestName: ${test.name}\nException: $ex")
            }
        }
    }
}
