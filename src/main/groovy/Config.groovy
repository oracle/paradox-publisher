/**
 * Parses the config file into a typesafe object
 */
class Config {
    static {
        ConfigSlurper slurper = new ConfigSlurper()
        def configFile = new File('conf/config.groovy')
        def config = slurper.parse(configFile.text)
        autoUrl = config.autoUrl
        couchUrl = config.couchUrl
        jiraUrl = config.jiraUrl
        scsUrl = config.scsUrl
        jiraUsername = config.jiraUsername
        jiraPassword = config.jiraPassword
        zapiUrl = config.zapiUrl
        jiraProjectKey = config.jiraProjectKey
        jiraProjectId = config.jiraProjectId
        hipchatToken = config.hipchatToken
        hipchatUrl = config.hipchatUrl
    }

    static String autoUrl
    static String couchUrl
    static String jiraUrl
    static String scsUrl
    static String jiraUsername
    static String jiraPassword
    static String zapiUrl
    static String jiraProjectKey
    static String jiraProjectId
    static String hipchatToken
    static String hipchatUrl
}
