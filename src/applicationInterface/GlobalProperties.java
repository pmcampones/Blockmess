package applicationInterface;

import java.util.Properties;

public class GlobalProperties {

	private static Properties props;

	public static Properties getProps() throws GlobalPropertiesNotInitializedException {
		if (props == null)
			throw new GlobalPropertiesNotInitializedException();
		return props;
	}

	public static void setProps(Properties props) throws GlobalPropertiesAlreadyInitializedException {
		if (GlobalProperties.props != null)
			throw new GlobalPropertiesAlreadyInitializedException();
		GlobalProperties.props = props;
	}

	public static class GlobalPropertiesAlreadyInitializedException extends RuntimeException {
		public GlobalPropertiesAlreadyInitializedException() {
			super("Properties already initialized");
		}
	}

	public static class GlobalPropertiesNotInitializedException extends RuntimeException {
		public GlobalPropertiesNotInitializedException() {
			super("Properties not yet initialized");
		}
	}

}
