import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Runs the tests by sending an http request to paradox-tester and then waits for the suite to finish
 */
@Log4j
class RunTests {
    def config = new Config()
    def rest = new RESTClient(config.autoUrl, 'application/json')

    static void main(String[] args) {
        def cli = new CliBuilder(
            usage: '''runTests [-h] <assembly> <guid> -- <testsToRun>
                |
                |arguments:
                | <assembly>     The name of the test suite to run
                | <environment>  The environment to run the tests against
                | <testsToRun>   The commandline parameters to pass to the test suite
                |
                |options:'''.stripMargin()
        )
        cli.with {
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

        String assembly = options.arguments()[0]
        String environment = options.arguments()[1]
        String testsToRun = options.arguments()[2..-1]
        log.info new RunTests().run(assembly, testsToRun, environment)
    }

    static String getCredentials() {
        def reader = new BufferedReader(new InputStreamReader(System.in))
        reader.ready() ? reader.readLine() : ''
    }

    String run(String assembly, testsToRun, String environment) {
        log.info "Running Tests '$assembly' '$testsToRun' '$environment'"
        URL url = postToQueue(testsToRun, environment, assembly)
        pollForFinished(url)
    }

    URL postToQueue(String assembly, String testsToRun, String environment) {
        def resp = rest.post(
            path: "queue/$assembly",
            body: [
                testsToRun: testsToRun,
                environment: environment,
                credentials: credentials
            ])

        new URL(resp.headers.Location as String)
    }

    String pollForFinished(URL url) {
        def item = (rest.get(path: url.path)).data
        while (item.Status == 'Running') {
            log.info 'sleeping 60 seconds'
            sleep(60000)
            item = (rest.get(path: url.path)).data
            log.info "item = $item"
        }

        url.path.split('/')[-1]
    }
}
