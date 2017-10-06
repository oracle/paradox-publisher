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
            dcsref: 'paradox',
            publisherVersion: PUBLISHER_VERSION,
        ]
        def queryString = event
            .findAll { it.value }
            .collectEntries { [it.key, URLEncoder.encode(it.value as String, 'UTF-8')] }
            .collect { "$it.key=$it.value" }.join('&')
        scs.get(uri: "$config.scsUrl?$queryString", headers: ['User-Agent': 'paradoxRestClient'])
    }

    def publish(String assemblyName, String executionGuid) {
        if (!config.with { autoUrl && scsUrl }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        scs = new RESTClient(config.scsUrl).with { parser.'image/gif' = parser.defaultParser; it }
        log.info "Fetching results from ${auto.uri}results/$assemblyName/$executionGuid ..."
        def results = auto.get(path: "results/$assemblyName/$executionGuid").data
        log.info "Results = $results"

        //Send Test Suite
        sendEvent(
            'wt.co_f': executionGuid,
            dcsuri: assemblyName,
            suiteName: assemblyName,
            environment: results.environment,
            date: results.date,
            time: results.time,
            commandLine: results.commandline,
            dscsip: InetAddress.localHost.hostName,
            dcsaut: System.properties.'user.name'
        )

        //Send Test Results
        for (test in results.tests) {
            sendEvent(
                'wt.co_f': executionGuid,
                dcsuri: test.name.replaceAll('\\.', '/'),
                testName: test.name,
                'wt.cg_n': test.labels.join(';'),
                state: test.state,
                performance: test.performance.toString(),
                defect: test.defect,
            )
        }
    }
}
