import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Publish results to couchdb
 */
@Log4j
class PublishToCouch implements Publisher {
    def auto = new RESTClient(config.autoUrl, 'application/json')
    def couch = new RESTClient(config.couchUrl, 'application/json')

    static void main(String[] args) {
        new PublishToCouch().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        def results = auto.get(path: "results/$assembly/$guid").data
        couch.put(path: "automation/$guid", body: results)
    }
}
