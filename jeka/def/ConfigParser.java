import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import proguard.ConfigurationParser;
import proguard.LineWordReader;

public class ConfigParser extends ConfigurationParser implements AutoCloseable {
	public ConfigParser(InputStream in, Properties properties) throws IOException {
		super(new LineWordReader(new LineNumberReader(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))), "InputStream '" + in + '\'', (File) null), properties);
	}
}