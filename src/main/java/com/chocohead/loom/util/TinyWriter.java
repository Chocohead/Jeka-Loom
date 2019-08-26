package com.chocohead.loom.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TinyWriter implements MappingProcessor, AutoCloseable {
	protected final Writer writer;

	public TinyWriter(Path file, String from, String to) throws IOException {
		writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		writer.write("v1\t");
		writer.write(from);
		writer.write('\t');
		writer.write(to);
		writer.write('\n');
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		try {
			writer.write("CLASS\t");
			writer.write(srcName);
			writer.write('\t');
			writer.write(dstName);
			writer.write('\n');
		} catch (IOException e) {
			throw new UncheckedIOException("Error writing tiny class", e);
		}
	}

	@Override
	public void acceptMethod(String srcClassName, String srcName, String desc, String dstClassName, String dstName) {
		try {
			writer.write("METHOD\t");
			writer.write(srcClassName);
			writer.write('\t');
			writer.write(desc);
			writer.write('\t');
			writer.write(srcName);
			writer.write('\t');
			writer.write(dstName);
			writer.write('\n');
		} catch (IOException e) {
			throw new UncheckedIOException("Error writing tiny method", e);
		}
	}

	@Override
	public void acceptMethodArg(String className, String methodName, String methodDesc, String dstClassName, int argIndex, int lvtIndex, String argName) {
	}

	@Override
	public void acceptMethodVar(String className, String methodName, String methodDesc, String dstClassName, int varIndex, int lvtIndex, String varName) {
	}

	@Override
	public void acceptField(String srcClassName, String srcName, String desc, String dstClassName, String dstName) {
		try {
			writer.write("FIELD\t");
			writer.write(srcClassName);
			writer.write('\t');
			writer.write(desc);
			writer.write('\t');
			writer.write(srcName);
			writer.write('\t');
			writer.write(dstName);
			writer.write('\n');
		} catch (IOException e) {
			throw new UncheckedIOException("Error writing tiny field", e);
		}
	}

	public void flush() throws IOException {
		if (writer != null) writer.flush();
	}

	@Override
	public void close() throws IOException {
		if (writer != null) writer.close();
	}
}