import org.apache.commons.cli.Option

/**
 * Runs a test suite, publishes the results to jira, couchdb and infinity, and emails interested parties
 */
class RunPublishNotify {
    def config = new Config()
    static void main(String[] args) {
        def cli = new CliBuilder(
            usage: '''runPublishNotify [-h] [-e <emails>...] <assembly> <environment> -- <testsToRun>...
                |
                |arguments:
                | <assembly>     The name of the test suite to run and publish
                | <environment>  The environment to run the tests against
                | <testsToRun>   The commandline parameters to pass to the test suite
                |options:'''.stripMargin()
        )
        cli.with {
            h longOpt: 'help', 'Show usage information'
            e longOpt: 'email', args: Option.UNLIMITED_VALUES, valueSeparator: ';',
                'The email address(es) results will be sent to'
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

        new RunPublishNotify().runPublishNotify(assembly, environment, options.emails ?: [], testsToRun)
    }

    void runPublishNotify(String assembly, String environment, List<String> email, String testsToRun) {
        def guid = RunTests.run(assembly, testsToRun, environment)
        new PrePublish().publish(assembly, guid)
        def jira = new PublishToJira().publish(assembly, guid)
        EmailCycleResults.email(
            jira.versionId as String,
            jira.cycleId as String,
            email,
            "$assembly Test Results for $environment",
            "$config.autoUrl/webtesting/results/$assembly/$guid",
            "Test ran: $testsToRun")

        new PublishToCouch().publish(assembly, guid)
        new PublishToInfinity().publish(assembly, guid)
    }
}
