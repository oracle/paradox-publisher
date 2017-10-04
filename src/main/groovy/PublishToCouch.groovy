import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Publish results to couchdb
 */
@Log4j
class PublishToCouch implements Publisher {
    RESTClient auto

    RESTClient couch

    static void main(String[] args) {
        new PublishToCouch().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        if (!config.with { autoUrl && couchUrl }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        couch = new RESTClient(config.couchUrl, 'application/json')
        def results = auto.get(path: "results/$assembly/$guid").data
        couch.put(path: "automation/$guid", body: results)
    }
}
