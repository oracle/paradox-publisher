/**
 * Encapsulates the standard publisher pattern that accepts an assembly name and a guid.
 */
trait Publisher {
    def config = new Config()

    def parseCommandline(String[] args) {
        def cli = new CliBuilder(
            usage: """${this.class.name.uncapitalize()} [-h] <assembly> <guid>
                |
                |arguments:
                | <assembly>  The name of the test suite to publish
                | <guid>      The guid of the test suite to publish
                |
                |options:""".stripMargin()
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
        String guid = options.arguments()[1]

        publish(assembly, guid)
    }

    abstract publish(String assembly, String guid)
}
