import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Publish results to couchdb
 */
@Log4j
class PublishToCouch implements Publisher {
    @Lazy
    def auto = { new RESTClient(config.autoUrl, 'application/json') } ()

    @Lazy
    def couch = { new RESTClient(config.couchUrl, 'application/json') } ()

    static void main(String[] args) {
        new PublishToCouch().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        if (!config.with { autoUrl && couchUrl }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        def results = auto.get(path: "results/$assembly/$guid").data
        couch.put(path: "automation/$guid", body: results)
    }
}
