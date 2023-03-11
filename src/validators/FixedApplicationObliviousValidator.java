package validators;

import lombok.experimental.Delegate;

public class FixedApplicationObliviousValidator implements ApplicationObliviousValidator {

    private static FixedApplicationObliviousValidator singleton;

    @Delegate
    private ApplicationObliviousValidator validator = new DefaultApplicationObliviousValidator();

    public static FixedApplicationObliviousValidator getSingleton() {
        if (singleton == null)
            singleton = new FixedApplicationObliviousValidator();
        return singleton;
    }

    public void setCustomValidator(ApplicationObliviousValidator validator) {
        this.validator = validator;
    }

}
