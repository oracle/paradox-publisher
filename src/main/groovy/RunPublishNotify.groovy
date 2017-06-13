/**
 * Runs a test suite, publishes the results to jira, couchdb and infinity, and emails interested parties
 */
class RunPublishNotify {
    static void main(String[] args) {
        def cli = new CliBuilder(usage:'runPublishNotify')
        cli.with {
            a required: true, longOpt: 'assembly', args: 1, 'The name of the test suite run or publish'
            e required: true, longOpt: 'environment', args: 1, 'The environment to run the tests against'
            h longOpt: 'help', 'Show usage information'
            m required: true, longOpt: 'email', args: 1, 'The email address(es) results will be sent to'
            t required: true, longOpt: 'testsToRun', args: 1, 'The commands to pass to the test suite'
            v required: true, longOpt: 'version', args: 1, 'The version for the jira cycle that gets created'
        }

        if (args?.grep(['-h', '--help'])) {
            cli.usage()
            return
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        runPublishNotify(options.a, options.e, options.m, options.t, options.v)
    }

    static void runPublishNotify(String assembly, String environment, String email, String testsToRun, String version) {
        def guid = RunTests.run(assembly, testsToRun, environment)
        PrePublish.prePublish(assembly, guid)
        def jira = PublishToJira.publish(version, assembly, guid)
        EmailCycleResults.email(
            jira.versionId as String,
            jira.cycleId as String,
            email,
            "$version Test Results for $environment",
            "$Config.autoUrl/webtesting/results/$assembly/$guid",
            "Test ran: $testsToRun")

        PublishToCouch.publish(assembly, guid)
        PublishToInfinity.publish(assembly, guid)
    }
}
