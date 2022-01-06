package com.oracle.infy.qa.paradoxpublisher

import groovy.util.logging.Log4j2
import groovyx.net.http.RESTClient

/**
 * Publishes results to a configurable hipchat channel
 */
@Log4j2
class PublishToHipchat implements Publisher {
    RESTClient auto

    RESTClient hipchat

    static void main(String[] args) {
        new PublishToHipchat().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        if (!config.with { autoUrl && hipchatToken && hipchatUrl && hipchatRoomId }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        hipchat = new RESTClient(config.hipchatUrl, 'application/json')
        hipchat.headers += [Authorization: "Bearer $config.hipchatToken"]
        def results = auto.get(uri: "${auto.uri}/results/$assembly/$guid").data
        hipchat.post(
            path: "room/$config.hipchatRoomId/notification",
            body: [
                style: 'application',
                url: "${auto.uri}/results/$assembly/$guid",
                format: 'medium',
                id: guid,
                title: results.name,
                description: 'pass/fail details'
            ]
        )
    }
}
