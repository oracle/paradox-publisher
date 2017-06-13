import groovyx.net.http.*
import groovy.util.logging.Log4j

/**
 * Publish results to infinity analytics
 */
@Log4j
class PublishToInfinity {
    static final PUBLISHER_VERSION = '0.1'
    static auto = new RESTClient(Config.autoUrl, 'application/json')
    static scs = new RESTClient().with { parser.'image/gif' = parser.defaultParser; it }

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'publishToInfinity')
        cli.with {
            a required: true, longOpt: 'assembly', args: 1, 'The name of the test suite run or publish'
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

    static sendEvent(Map event) {
        event << [
            dcsref: 'paradox',
            publisherVersion: PUBLISHER_VERSION,
        ]
        def queryString = event
            .findAll { it.value }
            .collectEntries { [it.key, URLEncoder.encode(it.value as String, 'UTF-8')] }
            .collect { "$it.key=$it.value" }.join('&')
        scs.get(uri: "$Config.scsUrl?$queryString", headers: ['User-Agent': 'paradoxRestClient'])
    }

    static void publish(String assemblyName, String executionGuid) {
        log.info "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
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
