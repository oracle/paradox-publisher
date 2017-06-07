/**
 * Runs a test suite, publishes the results to jira, couchdb and infinity, and emails interested parties
 */
class RunPublishNotify {
    static void main(String[] args) {
        def cli = new CliBuilder(
            usage: 'runPublishNotify [-h] [-a assembly] [-e environment] [-m email] [-t testsToRun] [-v version]')
        cli.with {
            h longOpt: 'help', 'Show usage information'
            a longOpt: 'assembly', args: 1, 'The name of the test suite run or publish'
            e longOpt: 'environment', args: 1, 'The environment to run the tests against'
            m longOpt: 'email', args: 1 'The email address(es) results will be sent to'
            t longOpt: 'testsToRun', args: 1, 'The commands to pass to the test suite'
            v longOpt: 'version', args: 1, 'The version for the jira cycle that will be created'
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        if (options.h) {
            cli.usage()
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
