package com.oracle.infy.qa.paradoxpublisher

import groovy.util.logging.Log4j2
import groovyx.net.http.RESTClient

/**
 * Runs the tests by sending an http request to paradox-tester and then waits for the suite to finish
 */
@Log4j2
class RunTests {
    def config = new Config()

    RESTClient auto

    static void main(String[] args) {
        def cli = new CliBuilder(
            usage: '''runTests [-h] <assembly> <environment> -- <testsToRun>
                |
                |arguments:
                | <assembly>     The name of the test suite to run
                | <environment>  The environment to run the tests against
                | <testsToRun>   The commandline parameters to pass to the test suite
                |
                |options:'''.stripMargin(),
            stopAtNonOption: false
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

        if (options.arguments().size() < 3) {
            cli.writer << 'error: Too few arguments\n'
            cli.usage()
            return
        }

        String assembly = options.arguments()[0]
        String environment = options.arguments()[1]
        String testsToRun = options.arguments()[2..-1].join(' ')
        log.info new RunTests().run(assembly, testsToRun, environment)
    }

    static String getCredentials() {
        def reader = new BufferedReader(new InputStreamReader(System.in))
        reader.ready() ? reader.readLine() : ''
    }

    String run(String assembly, String testsToRun, String environment) {
        if (!config.with { autoUrl }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        auto = new RESTClient(config.autoUrl, 'application/json')
        log.info "Running Tests '$assembly' '$testsToRun' '$environment'"
        URL url = postToQueue(assembly, testsToRun, environment)
        pollForFinished(url)
    }

    URL postToQueue(String assembly, String testsToRun, String environment) {
        def resp = auto.post(
            uri: "${auto.uri}/queue/$assembly",
            body: [
                testsToRun: testsToRun,
                environment: environment,
                credentials: credentials
            ])

        new URL(resp.headers.Location as String)
    }

    String pollForFinished(URL url) {
        def item = (auto.get(path: "$url.path")).data
        while (item.Status == 'Running') {
            log.info 'sleeping 60 seconds'
            sleep(60000)
            item = (auto.get(path: "$url.path")).data
            log.info "item = $item"
        }

        url.path.split('/')[-1]
    }
}
