import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Publishes results to a configurable hipchat channel
 */
@Log4j
class PublishToHipchat {
    static auto = new RESTClient(Config.autoUrl, 'application/json')
    static hipchat = new RESTClient(Config.hipchatUrl, 'application/json')

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'publishToHipchat')
        cli.with {
            a required: true, longOpt: 'assembly', args: 1, 'The name of the test suite to publish'
            g required: true, longOpt: 'guid', args: 1, 'The guid of the test suite to publish'
            h longOpt: 'help', 'Show usage information'
        }

        if (args?.grep(['-h', '--help'])) {
            cli.usage()
            return
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        publish(options.a, options.g)
    }

    static void publish(String assembly, String guid) {
        hipchat.headers += [Authorization: "Bearer $Config.hipchatToken"]
        def results = auto.get(path: "results/$assembly/$guid").data
        hipchat.post(
            path: "room/$Config.hipchatRoomid/notification",
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
