import org.apache.commons.math3.stat.descriptive.StatisticalSummary

/**
 * Bean for test results from paradox-publishers
 */
class TestResult {
    String name
    String state
    String defect
    String comment
    StatisticalSummary performance
}
