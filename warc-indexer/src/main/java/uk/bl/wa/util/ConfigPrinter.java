/**
 * 
 */
package uk.bl.wa.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class ConfigPrinter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Load the config:
		Config config = ConfigFactory.load();
		
		// Set up to avoid printing internal details:
		ConfigRenderOptions options =
				ConfigRenderOptions.defaults().setOriginComments(false);
		
		// Print the standard config to STDOUT:
		System.out.println(config.withOnlyPath("warc").root().render( options ));
	}

}