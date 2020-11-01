package net.serenitybdd.junit5;

import net.thucydides.core.configuration.SystemPropertiesConfiguration;
import net.thucydides.core.model.DataTable;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.pages.Pages;
import net.thucydides.core.reports.ReportService;
import net.thucydides.core.steps.*;
import net.thucydides.core.util.SystemEnvironmentVariables;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static net.thucydides.core.reports.ReportService.getDefaultReporters;
import static net.thucydides.core.steps.TestSourceType.TEST_SOURCE_JUNIT;

public class SerenityTestExecutionListener implements TestExecutionListener {

    private final Logger logger = LoggerFactory.getLogger(SerenityTestExecutionListener.class);

    private ReportService reportService;

    private TestPlan currentTestPlan;

    private SerenityTestExecutionSummary summary;

    private Pages pages;

    //key-> "ClassName.MethodName"
    //entries-> DataTable associated with method
    private Map<String,DataTable> dataTables;

    private int parameterSetNumber = 0;

    private BaseStepListener baseStepListener;

    private Class<?> testClass;

    public SerenityTestExecutionListener() {
        File outputDirectory = getOutputDirectory();
        baseStepListener = Listeners.getBaseStepListener().withOutputDirectory(outputDirectory);
        StepEventBus.getEventBus().registerListener(baseStepListener);
    }

    private File getOutputDirectory() {
        SystemPropertiesConfiguration systemPropertiesConfiguration = new SystemPropertiesConfiguration(new SystemEnvironmentVariables());
        return systemPropertiesConfiguration.getOutputDirectory();
    }

    private BaseStepListener getBaseStepListener() {
        return baseStepListener;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
        this.summary = new SerenityTestExecutionSummary(testPlan);
        logger.info("->TestPlanExecutionStarted " + testPlan);
        Set<TestIdentifier> roots = testPlan.getRoots();
        for(TestIdentifier root: roots) {
            logger.info("TestIdentifier Root " + root.getUniqueId() + root.getDisplayName() + root.getSource());
            Set<TestIdentifier> children = testPlan.getChildren(root.getUniqueId());
            for (TestIdentifier child : children) {
                //System.out.println("TestIdentifier Child " + child.getUniqueId() + child.getDisplayName() + child.getSource() + child.getType());
                if(isClassSource(child))
                {
                    ClassSource classSource = (ClassSource)child.getSource().get();
                    testClass = classSource.getJavaClass();
                    //logger.info("Java Class " + classSource.getJavaClass());
                    //logger.info("Class " + classSource.getClass());
                    //StepEventBus.getEventBus().testSuiteStarted(classSource.getJavaClass());
                    //startTestSuiteForFirstTest(classSource.getJavaClass());
                    //injectScenarioStepsInto(classSource.getJavaClass());
                    dataTables = JUnit5DataDrivenAnnotations.forClass(((ClassSource)child.getSource().get()).getJavaClass()).getParameterTables();
                    //System.out.println("AAA " + dataTables);
                }
            }
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {

        logger.info("->TestPlanExecutionFinished " + testPlan);
       // notifyTestSuiteFinished();
        if(dataTables == null) {
            generateReports();
        } else {
            generateReportsForParameterizedTest();
            dataTables = null;
        }
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        processTestMethodAnnotationsFor(testIdentifier);
    }

    private void processTestMethodAnnotationsFor(TestIdentifier testIdentifier)  {
        Optional<TestSource> testSource = testIdentifier.getSource();
        if( testSource.isPresent() && (testSource.get() instanceof MethodSource)) {
            MethodSource methodTestSource = ((MethodSource)testIdentifier.getSource().get());
            String className =  methodTestSource.getClassName();
            String methodName = methodTestSource.getMethodName();
            //method parameter types are class names as strings comma separated : java.langString,java.lang.Integer
            String methodParameterTypes = methodTestSource.getMethodParameterTypes();
            List<Class> methodParameterClasses = null;

            if(methodParameterTypes != null) {
                methodParameterClasses = Arrays.asList(methodParameterTypes.split(",")).stream().map(parameterClassName -> {
                    try {
                        //ClassUtils handles also simple data type like int, char..
                        return ClassUtils.forName(parameterClassName.trim(),this.getClass().getClassLoader());
                    } catch (ClassNotFoundException e) {
                        logger.error("Problem when getting parameter classes ",e);
                        return null;
                    }
                }).collect(Collectors.toList());
            }
            try {
                if (isIgnored(getProcessedMethod(className, methodName,methodParameterClasses))) {
                   startTestAtEventBus(testIdentifier);
                   StepEventBus.getEventBus().testIgnored();
                   StepEventBus.getEventBus().testFinished();
               }
            } catch(ClassNotFoundException | NoSuchMethodException exception) {
                logger.error("Exception when processing method annotations", exception);
            }
        }
    }

    @NotNull
    private Method getProcessedMethod(String className, String methodName,  List<Class> methodParameterClasses ) throws NoSuchMethodException, ClassNotFoundException {
        if (methodParameterClasses != null) {
            Class[] classesArray =  new Class[methodParameterClasses.size()];
            return Class.forName(className).getMethod(methodName, methodParameterClasses.toArray(classesArray));
        }
        else {
            return Class.forName(className).getMethod(methodName);
        }
    }

    private boolean isIgnored(Method child) {
        return child.getAnnotation(Disabled.class) != null;
    }


    private void startTestAtEventBus(TestIdentifier testIdentifier) {
        StepEventBus.getEventBus().setTestSource(TestSourceType.TEST_SOURCE_JUNIT.getValue());
        String displayName = removeEndBracketsFromDisplayName(testIdentifier.getDisplayName());
        if( isMethodSource(testIdentifier) ) {
            String className = ((MethodSource) testIdentifier.getSource().get()).getClassName();
            String methodName = ((MethodSource) testIdentifier.getSource().get()).getMethodName();
            try {
                StepEventBus.getEventBus().testStarted(
                        Optional.ofNullable(displayName).orElse("Initialisation"),
                        Class.forName(className));
            } catch(ClassNotFoundException  exception) {
                logger.error("Exception when starting test at event bus ", exception);
            }
        }
    }

    private String removeEndBracketsFromDisplayName(String displayName){
        if(displayName != null && displayName.endsWith("()")) {
            displayName = displayName.substring(0,displayName.length()-2);
        }
        return displayName;
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        logger.info("-->Execution started " + testIdentifier.getDisplayName() +"--" +testIdentifier.getType() + "--" +testIdentifier.getSource());
        if(!testIdentifier.getSource().isPresent()) {
            logger.info("No action done at executionStarted because testIdentifier is null" );
            return;
        }
        //TODO
        if(isTestContainer(testIdentifier) && isClassSource(testIdentifier))  {
            logger.info("-->TestSuiteStarted " + ((ClassSource)testIdentifier.getSource().get()).getJavaClass() );
            StepEventBus.getEventBus().testSuiteStarted( ((ClassSource)testIdentifier.getSource().get()).getJavaClass());
        }

        if(isMethodSource(testIdentifier)) {
            MethodSource methodSource = ((MethodSource)testIdentifier.getSource().get());
            if(isSimpleTest(testIdentifier)) {
                testStarted(methodSource,testIdentifier);
            }
            String sourceMethod = methodSource.getClassName() + "." + methodSource.getMethodName();
            logger.info("GetDataTable Formethod " + sourceMethod);
            DataTable dataTable = dataTables.get(sourceMethod);
            logger.info("FoundDataTable " + dataTable + " " + dataTable.getRows());

            if(dataTable != null) {
                if(isTestContainer(testIdentifier)){
                    //StepEventBus.getEventBus().useExamplesFrom(dataTable);
                    //logger.info("-->EventBus.useExamplesFrom" + dataTable);
                    parameterSetNumber = 0;
                } else if(isSimpleTest(testIdentifier)){
                    StepEventBus.getEventBus().useExamplesFrom(dataTable);
                    logger.info("-->EventBus.useExamplesFrom" + dataTable);
                    logger.info("-->EventBus.exampleStarted " +  parameterSetNumber + "--" + dataTable.row(parameterSetNumber).toStringMap());
                    StepEventBus.getEventBus().exampleStarted(dataTable.row(parameterSetNumber).toStringMap());
                }
            }
        }
    }

    private boolean isTestContainer(TestIdentifier testIdentifier) {
        return testIdentifier.getType() == TestDescriptor.Type.CONTAINER;
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        logger.info("-->Execution finished " + testIdentifier.getDisplayName() + "--" +testIdentifier.getType() + "--" + testIdentifier.getSource() +" with result " + testExecutionResult.getStatus());
        if(!testIdentifier.getSource().isPresent()) {
            logger.info("No action done at executionFinished because testIdentifier is null" );
            return;
        }
        //TODO - check this
        /**
         * logger.info("-->TestSuiteFinished " + ((ClassSource)testIdentifier.getSource().get()).getJavaClass() );
         *             StepEventBus.getEventBus().testSuiteFinished();
         */
        if(isTestContainer(testIdentifier) && isClassSource(testIdentifier)) {
            logger.info("-->TestSuiteFinished " + ((ClassSource)testIdentifier.getSource().get()).getJavaClass() );
            StepEventBus.getEventBus().testSuiteFinished();
        }
        if(isSimpleTest(testIdentifier)){
            if(isMethodSource(testIdentifier)) {
                testFinished(testIdentifier);
                MethodSource methodSource = ((MethodSource)testIdentifier.getSource().get());
                String sourceMethod = methodSource.getClassName() + "." + methodSource.getMethodName();
                DataTable dataTable = dataTables.get(sourceMethod);
                if(dataTable != null) {
                    logger.info("-->EventBus.exampleFinished " +  parameterSetNumber + "--" + dataTable.row(parameterSetNumber).toStringMap());
                    StepEventBus.getEventBus().exampleFinished();
                    parameterSetNumber++;
                }
            }
        }
        switch (testExecutionResult.getStatus()) {

            case SUCCESSFUL: {
                if (testIdentifier.isContainer()) {
                    this.summary.containersSucceeded.incrementAndGet();
                    //System.out.println("CoNTAINER OK");
                }
                if (testIdentifier.isTest()) {
                    this.summary.testsSucceeded.incrementAndGet();
                    //System.out.println("TEST OK");
                }
                break;
            }

            case ABORTED: {
                if (testIdentifier.isContainer()) {
                    this.summary.containersAborted.incrementAndGet();
                }
                if (testIdentifier.isTest()) {
                    this.summary.testsAborted.incrementAndGet();
                }
                break;
            }

            case FAILED: {
                if (testIdentifier.isContainer()) {
                    this.summary.containersFailed.incrementAndGet();
                }
                if (testIdentifier.isTest()) {
                    this.summary.testsFailed.incrementAndGet();
                }
                testExecutionResult.getThrowable().ifPresent(
                        throwable -> this.summary.addFailure(testIdentifier, throwable));
                StepEventBus.getEventBus().testFailed(testExecutionResult.getThrowable().get());
                break;
            }
            default:
                throw new PreconditionViolationException(
                        "Unsupported execution status:" + testExecutionResult.getStatus());
        }
    }

    private boolean isSimpleTest(TestIdentifier testIdentifier) {
        return testIdentifier.getType() == TestDescriptor.Type.TEST;
    }


    /*public void testFailure(final Failure failure) throws Exception {
        if (testingThisTest(failure.getDescription())) {
            startTestIfNotYetStarted(failure.getDescription());
            stepEventBus().testFailed(failure.getException());
            updateFailureList(failure);
            endTest();
        }
    }*/

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        logger.info("-->ReportingEntryPublished " + testIdentifier.getDisplayName() + "--" +testIdentifier.getType() + "--" + testIdentifier.getSource());
    }

    private boolean isClassSource(TestIdentifier testId){
        return testId.getSource().isPresent() && (testId.getSource().get() instanceof ClassSource);
    }

    private boolean isMethodSource(TestIdentifier testId){
        return testId.getSource().isPresent() && (testId.getSource().get() instanceof MethodSource);
    }



    /**
     * Called when a test starts. We also need to start the test suite the first
     * time, as the testRunStarted() method is not invoked for some reason.
     */
    //@Override
    private void testStarted(MethodSource methodSource,TestIdentifier testIdentifier/*final Description description*/) {
        if (testingThisTest(testIdentifier)) {
            startTestSuiteForFirstTest(testIdentifier);
            logger.info(Thread.currentThread() + " Test started " + testIdentifier);
            StepEventBus.getEventBus().clear();
            StepEventBus.getEventBus().setTestSource(TEST_SOURCE_JUNIT.getValue());
            StringBuffer testName = new StringBuffer(methodSource.getMethodName());
            if(testIdentifier.getDisplayName() != null)
            {
                testName.append("%" + testIdentifier.getDisplayName());
            }
            StepEventBus.getEventBus().testStarted(
                    Optional.ofNullable(testName.toString()).orElse("Initialisation"),
                    methodSource.getJavaClass());
        }
    }

    private boolean testingThisTest(TestIdentifier testIdentifier) {
        if(isMethodSource(testIdentifier))
        {
            MethodSource methodSource = (MethodSource)testIdentifier.getSource().get();
            if(testClass.equals(methodSource.getJavaClass())) {
                return true;
            }
        }
        return false;
    }



    private void startTestSuiteForFirstTest(TestIdentifier testIdentifier) {
        //if (!getBaseStepListener().testSuiteRunning()) {
            //TODO
            if(isMethodSource(testIdentifier))  {
                logger.info("-->TestSuiteStarted " + ((MethodSource)testIdentifier.getSource().get()).getJavaClass() );
                StepEventBus.getEventBus().testSuiteStarted( ((MethodSource)testIdentifier.getSource().get()).getJavaClass());
            }
        //}
    }

    /*private void startTestSuiteForFirstTest(Class<?> javaClass) {
        if (!getBaseStepListener().testSuiteRunning()) {
            StepEventBus.getEventBus().testSuiteStarted(javaClass);
        }
    }*/

    private void testFinished(TestIdentifier testIdentifier)  {
        logger.info("Test finished " + testIdentifier);
        //if (testingThisTest(description)) {
            //TODO
            //updateResultsUsingTestAnnotations(description);
            StepEventBus.getEventBus().testFinished();
            StepEventBus.getEventBus().setTestSource(TEST_SOURCE_JUNIT.getValue());
            //endTest();
        //}
    }

    private void notifyTestSuiteFinished() {
        try {
            //if (dataDrivenTest()) {
            //    StepEventBus.getEventBus().exampleFinished();
            //} else {
                StepEventBus.getEventBus().testSuiteFinished();
            //}
        } catch (Throwable listenerException) {
            // We report and ignore listener exceptions so as not to mess up the rest of the test mechanics.
            logger.error("Test event bus error: " + listenerException.getMessage(), listenerException);
        }
    }


    /**
     * Find the current set of test outcomes produced by the test execution.
     * @return the current list of test outcomes
     */
    public List<TestOutcome> getTestOutcomes() {
        return baseStepListener.getTestOutcomes();
    }


    private void generateReports() {
        generateReportsFor(getTestOutcomes());
    }

    private void generateReportsForParameterizedTest(){
        ParameterizedTestsOutcomeAggregator parameterizedTestsOutcomeAggregator = new ParameterizedTestsOutcomeAggregator();
        generateReportsFor(parameterizedTestsOutcomeAggregator.aggregateTestOutcomesByTestMethods());
    }



    /**
     * A test runner can generate reports via Reporter instances that subscribe
     * to the test runner. The test runner tells the reporter what directory to
     * place the reports in. Then, at the end of the test, the test runner
     * notifies these reporters of the test outcomes. The reporter's job is to
     * process each test run outcome and do whatever is appropriate.
     *
     * @param testOutcomeResults the test results from the previous test run.
     */
    private void generateReportsFor(final List<TestOutcome> testOutcomeResults) {
        getReportService().generateReportsFor(testOutcomeResults);
        getReportService().generateConfigurationsReport();
    }

    private ReportService getReportService() {
        if (reportService == null) {
            reportService = new ReportService(getOutputDirectory(), getDefaultReporters());
        }
        return reportService;
    }

    /**
     * Instantiates the @ManagedPages-annotated Pages instance using current WebDriver.
     * @param testCase A Serenity-annotated test class
     */
    protected void injectAnnotatedPagesObjectInto(final Object testCase) {
        StepAnnotations.injector().injectAnnotatedPagesObjectInto(testCase, pages);
    }

}