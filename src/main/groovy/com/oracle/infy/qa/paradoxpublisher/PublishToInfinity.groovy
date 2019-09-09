package com.oracle.infy.qa.paradoxpublisher

import groovy.json.JsonBuilder
import groovy.util.logging.Log4j2
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

/**
 * Publish results to infinity analytics
 */
@Log4j2
class PublishToInfinity implements Publisher {
    static final PUBLISHER_VERSION = '0.2'
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
        log.info "Sending GET ${config.scsUrl}?$queryString"
        scs.get(uri: "$config.scsUrl?$queryString", headers: ['User-Agent': 'InfinityParadoxPublisher'])
    }

    def sendEvents(List<Map<String, ?>> events) {
        log.info "Preparing to POST ${events.size()} events to ${config.scsUrl}"
        List<List<Map<String, ?>>> subLists = events.collate(config.scsPostBatchSize)
        subLists.each { sl ->
            def body = serializeEvents(sl)
            log.info "Sending ${sl.size()} events to ${config.scsUrl}"
            log.debug "headers: ${new JsonBuilder(scs.headers).toPrettyString()}"
            log.debug "body: ${new JsonBuilder(body).toPrettyString()}"
            try {
                scs.post(
                    uri: config.scsUrl,
                    body: body,
                    requestContentType: ContentType.JSON,
                    headers: [
                        'User-Agent': 'InfinityParadoxPublisher',
                        'Content-Type': 'application/json',
                        'Referer': 'paradox-publisher'
                    ]
                )
            } catch (HttpResponseException ex) {
                log.error ("Event Send Threw Exception: $ex")
            }
        }

    }

    def initializeRestClients() {
        if (!config.with { autoUrl && scsUrl && scsRequestType }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        } else if (!config.with {
            ( scsRequestType == 'POST' && scsPostBatchSize > 0 ) ||
                scsRequestType == 'GET' }) {
            log.warn """Invalid scsRequestType or scsPostBatchSize values:
Expected scsRequestType = 'POST' or 'GET', received ${config.scsRequestType}
Expected scsPostBatchSize > 0 if scsRequestType = 'POST': received ${config.scsPostBatchSize}
unable to execute ${this.getClass().name}"""
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        scs = new RESTClient(config.scsUrl)
        if (config.proxy) {
            scs.setProxy(config.proxy.host, config.proxy.port, config.proxy.scheme)
        }
    }

    def publish(String assemblyName, String executionGuid) {
        initializeRestClients()

        log.info "Fetching results from ${auto.uri}/results/$assemblyName/$executionGuid ..."
        def results = auto.get(uri: "${auto.uri}/results/$assemblyName/$executionGuid").data
        log.debug "Results = $results"

        // Send Test Results
        log.info "Sending ${results.tests.size()} test case results to Infinity"
        List<Map<String, ?>> events = []
        for (test in results.tests) {
            Map<String, ?> evt = [
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
            if (config.scsRequestType == 'POST') {
                events.add evt
            } else if (config.scsRequestType == 'GET') {
                try {
                    sendEvent(evt)
                } catch (HttpResponseException ex) {
                    log.error ("Was unable to send event to Infinity.\ntestName: ${test.name}\nException: $ex")
                }
            }
        }

        if (events) { sendEvents(events) }
    }

    /**
     * Creates a string representation of a collection of events in the following format:
     *
     * {
     *     "static": {
     *         "commonParam1":"val",
     *         "commonParam2":"val"
     *     },
     *     "events": [
     *         {"uniqueParam1":"val", "uniqueParam2":"val"},
     *         {"uniqueParam1":"val2"}
     *     ]
     * }
     *
     * @param events The events to serialize
     * @return The serialized events
     */
    static Map<String, ?> serializeEvents(Iterable<Map<String, ?>> events) {
        def commonMap = events.inject { a, e -> a.intersect e }

        [
            static: commonMap,
            events: events.collect { e -> e.findAll { !(it.key in ['userAgent', 'referer']) } - commonMap }
        ]
    }
}
