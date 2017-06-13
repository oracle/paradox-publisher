import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Publishes results to a configurable hipchat channel
 */
@Log4j
class PublishToHipchat implements Publisher {
    def auto = new RESTClient(config.autoUrl, 'application/json')
    def hipchat = new RESTClient(config.hipchatUrl, 'application/json')

    static void main(String[] args) {
        new PublishToHipchat().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        hipchat.headers += [Authorization: "Bearer $config.hipchatToken"]
        def results = auto.get(path: "results/$assembly/$guid").data
        hipchat.post(
            path: "room/$config.hipchatRoomid/notification",
            body: [
                style: 'application',
                url: "$auto.uri/results/$assembly/$guid",
                format: 'medium',
                id: guid,
                title: results.name,
                description: 'pass/fail details'
            ]
        )
    }
}
