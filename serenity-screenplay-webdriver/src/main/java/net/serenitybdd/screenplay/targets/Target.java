package net.serenitybdd.screenplay.targets;

import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.abilities.BrowseTheWeb;
import net.serenitybdd.screenplay.ui.LocatorStrategies;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static net.serenitybdd.core.pages.RenderedPageObjectView.containingTextAndMatchingCSS;

public abstract class Target {

    protected final String targetElementName;
    protected final Optional<IFrame> iFrame;
    protected final Optional<Duration> timeout;

    public Target(String targetElementName, Optional<IFrame> iFrame) {
        this.targetElementName = targetElementName;
        this.iFrame = iFrame;
        this.timeout = Optional.empty();
    }

    public Target(String targetElementName, Optional<IFrame> iFrame, Optional<Duration> timeout) {
        this.targetElementName = targetElementName;
        this.iFrame = iFrame;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return targetElementName;
    }

    public static TargetBuilder the(String targetElementName) {
        return new TargetBuilder(targetElementName);
    }

    protected PageObject currentPageVisibleTo(Actor actor) {
        return TargetResolver.create(BrowseTheWeb.as(actor).getDriver(), this);
    }

    public WebElementFacade resolveFor(Actor actor) {
        return resolveFor(currentPageVisibleTo(actor));
    }

    public List<WebElementFacade> resolveAllFor(Actor actor) {
        return resolveAllFor(currentPageVisibleTo(actor));
    }

    public abstract WebElementFacade resolveFor(PageObject page);
    public abstract List<WebElementFacade> resolveAllFor(PageObject page);

    public abstract Target called(String name);

    public abstract SearchableTarget of(String... parameters);

    public abstract String getCssOrXPathSelector();

    public Optional<IFrame> getIFrame() {
        return iFrame;
    }

    public String getName() {
        return targetElementName;
    }

    public abstract Target waitingForNoMoreThan(Duration timeout);

    public Target inside(String locator) {
        return inside(Target.the("Containing element").locatedBy(locator));
    }

    public Target inside(Target container) {
        return Target.the(getName()).locatedBy(
                LocatorStrategies.findNestedElements(container, this)
        );
    }

    public abstract List<By> selectors(WebDriver driver);
}
