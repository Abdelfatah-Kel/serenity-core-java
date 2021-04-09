package net.serenitybdd.crossbrowsertesting;

import net.serenitybdd.core.webdriver.OverrideDriverCapabilities;
import net.serenitybdd.core.webdriver.driverproviders.AddCustomDriverCapabilities;
import net.thucydides.core.model.Story;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.util.MockEnvironmentVariables;
import net.thucydides.core.webdriver.SupportedWebDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WhenAddingCrossBrowserTestingCapabilities {
    EnvironmentVariables environmentVariables = new MockEnvironmentVariables();

    private static final TestOutcome SAMPLE_TEST_OUTCOME = TestOutcome.forTestInStory("sample_test", Story.called("Sample story"));

    @BeforeEach
    public void prepareSession() {
        OverrideDriverCapabilities.clear();
    }

    @Test
    public void thePlatformShouldBeAddedToTheCapability() {
        DesiredCapabilities capabilities = new DesiredCapabilities();

        environmentVariables.setProperty("remote.platform","android");

        Capabilities enhancedCapabilities = AddCustomDriverCapabilities.from(environmentVariables)
                .withTestDetails(SupportedWebDriver.REMOTE, SAMPLE_TEST_OUTCOME)
                .to(capabilities);

        assertThat(enhancedCapabilities.getPlatformName()).isEqualTo(Platform.ANDROID);
    }

    @Test
    public void theBrowserNameShouldBeAddedDirectlyToTheCapability() {

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless");
        DesiredCapabilities capabilities = new DesiredCapabilities(chromeOptions);

        Capabilities enhancedCapabilities = AddCustomDriverCapabilities.from(environmentVariables)
                .withTestDetails(SupportedWebDriver.REMOTE, SAMPLE_TEST_OUTCOME)
                .to(capabilities);

        assertThat(enhancedCapabilities.getBrowserName()).isEqualTo("chrome");
    }

    @Test
    public void theBuildNameCanBeSpecifiedInTheCrossBrowserTestingConfiguration() {
        DesiredCapabilities capabilities = new DesiredCapabilities();

        environmentVariables.setProperty("crossbrowsertesting.build","sample build");
        Capabilities enhancedCapabilities = AddCustomDriverCapabilities.from(environmentVariables)
                .withTestDetails(SupportedWebDriver.REMOTE, SAMPLE_TEST_OUTCOME)
                .to(capabilities);

        assertThat(cbtOptionsFrom(enhancedCapabilities).get("build")).isEqualTo("sample build");
    }

    @Test
    public void theSessionNameShouldBeTakenFromTheNameOfTheTest() {

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless");
        // Given
        DesiredCapabilities capabilities = new DesiredCapabilities(chromeOptions);

        Capabilities enhancedCapabilities = AddCustomDriverCapabilities.from(environmentVariables)
                .withTestDetails(SupportedWebDriver.REMOTE, SAMPLE_TEST_OUTCOME)
                .to(capabilities);

        assertThat(cbtOptionsFrom(enhancedCapabilities).get("name")).isEqualTo("Sample story - Sample test");
    }

    @Test
    public void theBuildCanBeOverridenAtRunTime() {
        DesiredCapabilities capabilities = new DesiredCapabilities();

        environmentVariables.setProperty("crossbrowsertesting.build","sample build");
        OverrideDriverCapabilities.withProperty("crossbrowsertesting.build").setTo("overridden build");

        Capabilities enhancedCapabilities = AddCustomDriverCapabilities.from(environmentVariables)
                .withTestDetails(SupportedWebDriver.REMOTE, SAMPLE_TEST_OUTCOME)
                .to(capabilities);

        assertThat(cbtOptionsFrom(enhancedCapabilities).get("build")).isEqualTo("overridden build");
    }

    private Map<String,String> cbtOptionsFrom(Capabilities capabilities) {
        return (Map<String, String>) capabilities.getCapability("cbt:options");
    }
}
