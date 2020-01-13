package com.chocohead.loom.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TinyParamWriter extends TinyWriter {
	protected final Writer parameters;
	protected final Set<String> seenMethods = new HashSet<>();
	protected final List<String> currentMethodArgs = new ArrayList<>();
	protected String currentMethod;

	public TinyParamWriter(Path mappings, Path parameters, String from, String to) throws IOException {
		super(mappings, from, to);

		this.parameters = Files.newBufferedWriter(parameters, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Override
	public void acceptMethodArg(String className, String methodName, String methodDesc, String dstClassName, int argIndex, String argName) {
		if (currentMethod != null && !currentMethod.equals(dstClassName + '/' + methodName + methodDesc)) {
			assert !currentMethodArgs.isEmpty();

			try {
				writer.write(currentMethod);
				writer.write('\n');
				for (int i = currentMethodArgs.size() - 1; i >= 0; i--) {
					String arg = currentMethodArgs.get(i);

					if (arg != null) {
						writer.write('\t');
						writer.write(Integer.toString(i));
						writer.write(':');
						writer.write(' ');
						writer.write(arg);
						writer.write('\n');
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Error writing tiny method", e);
			}

			currentMethodArgs.clear();
		}

		assert currentMethodArgs.isEmpty() || (dstClassName + '/' + methodName + methodDesc).equals(currentMethod);
		if (currentMethodArgs.isEmpty()) currentMethod = dstClassName + '/' + methodName + methodDesc;

		while (currentMethodArgs.size() <= argIndex) currentMethodArgs.add(null);
		String last = currentMethodArgs.set(argIndex, argName);
		assert last == null;
	}

	@Override
	public void flush() throws IOException {
		try {
			super.flush();
		} finally {
			parameters.flush();
		}
	}

	@Override
	public void close() throws IOException {
		try {
			super.close();
		} finally {
			parameters.close();
		}
	}
}
