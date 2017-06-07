import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Runs the tests by sending an http request to paradox-tester and then waits for the suite to finish
 */
@Log4j
class RunTests {
    static rest = new RESTClient(Config.autoUrl, 'application/json')

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'runTests [-a assembly] [-t testsToRun] [-e environment]')
        cli.with {
            a longOpt: 'assembly', args: 1, 'The name of the test suite run or publish'
            e longOpt: 'environment', args: 1, 'The environment to run the tests against'
            t longOpt: 'testsToRun', args: 1, 'The commands to pass to the test suite'
            h longOpt: 'help', 'Show usage information'
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        if (options.h) {
            cli.usage()
            return
        }

        log.info run(options.a, options.t, options.e)
    }

    static String getCredentials() {
        def reader = new BufferedReader(new InputStreamReader(System.in))
        reader.ready() ? reader.readLine() : ''
    }

    static String run(String assembly, String testsToRun, String environment) {
        log.info "Running Tests '$assembly' '$testsToRun' '$environment'"
        URL url = postToQueue(testsToRun, environment, assembly)
        pollForFinished(url)
    }

    static URL postToQueue(String assembly, String testsToRun, String environment) {
        def resp = rest.post(
            path: "queue/$assembly",
            body: [
                testsToRun: testsToRun,
                environment: environment,
                credentials: credentials
            ])

        new URL(resp.headers.Location as String)
    }

    static String pollForFinished(URL url) {
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
