package com.chocohead.loom.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.stream.Stream;

public class EnigmaReader {
	static final boolean LEGACY = true;

	public static void readFrom(Path dir, MappingProcessor processor) throws IOException {
		try (Stream<Path> stream = Files.find(FileSystems.newFileSystem(dir, null).getPath("/"),
				Integer.MAX_VALUE,
				(path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".mapping"),
				FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(file -> readEnigmaFile(file, processor));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static void readEnigmaFile(Path file, MappingProcessor processor) {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;
			Queue<String> contextStack = Collections.asLifoQueue(new ArrayDeque<>());
			Queue<String> contextNamedStack = Collections.asLifoQueue(new ArrayDeque<>());
			int indent = 0;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				int newIndent = 0;
				while (newIndent < line.length() && line.charAt(newIndent) == '\t') newIndent++;
				int indentChange = newIndent - indent;

				if (indentChange != 0) {
					if (indentChange < 0) {
						for (int i = 0; i < -indentChange; i++) {
							contextStack.remove();
							contextNamedStack.remove();
						}

						indent = newIndent;
					} else {
						throw new IOException("Invalid enigma line (invalid indentation change): " + line);
					}
				}

				line = line.substring(indent);
				String[] parts = line.split(" ");

				switch (parts[0]) {
				case "CLASS":
					if (parts.length < 2 || parts.length > 3) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String obfName = parts[1];
					if (indent >= 1 && obfName.contains("/")) {//Some inner classes carry the named outer class, others the obf'd outer class
						int split = obfName.lastIndexOf('$');
						assert split > 2; //Should be at least a/b$c
						String context = contextStack.peek();
						if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (named inner class without outer class name): " + line);
						obfName = context.substring(1) + '$' + obfName.substring(split + 1);
					}
					contextStack.add('C' + obfName);
					indent++;
					if (parts.length == 3) {
						String className;
						if (indent > 1) {//If we're an indent in, we're an inner class so want the outer classes's name
							StringBuilder classNameBits = new StringBuilder(parts[2]);
							String context = contextNamedStack.peek();
							if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (named inner class without outer class name): " + line);
							//Named inner classes shouldn't ever carry the outer class's package + name
							assert !parts[2].startsWith(context.substring(1)): "Pre-prefixed enigma class name: " + parts[2];
							classNameBits.insert(0, '$');
							classNameBits.insert(0, context.substring(1));
							className = classNameBits.toString();
						} else {
							className = parts[2];
						}
						contextNamedStack.add('C' + className);
						processor.acceptClass(obfName, className);
					} else {
						contextNamedStack.add('C' + obfName); //No name, but we still need something to avoid underflowing
					}
					break;

				case "METHOD": {
					if (parts.length < 3 || parts.length > 4) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					if (!parts[parts.length - 1].startsWith("(")) throw new IOException("Invalid enigma line (invalid method desc): " + line);
					String context = contextStack.peek();
					if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (method without class): " + line);
					contextStack.add('M' + parts[1] + parts[parts.length - 1]);
					indent++;
					if (parts.length == 4) {
						processor.acceptMethod(context.substring(1), parts[1], parts[3], contextNamedStack.peek(), parts[2]);
						contextNamedStack.add('M' + parts[2]);
					} else {
						contextNamedStack.add('M' + parts[1]); //No name, but we still need something to avoid underflowing
					}
					break;
				}

				case "ARG":
				case "VAR": {
					if (parts.length != 3) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String methodContext = contextStack.poll();
					if (methodContext == null || methodContext.charAt(0) != 'M') throw new IOException("Invalid enigma line (arg without method): " + line);
					String classContext = contextStack.peek();
					if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing method owner context from stack");
					contextStack.add(methodContext);
					int methodDescStart = methodContext.indexOf('(');
					assert methodDescStart != -1;

					String srcClsName = classContext.substring(1);
					String srcMethodName = methodContext.substring(1, methodDescStart);
					String srcMethodDesc = methodContext.substring(methodDescStart);
					int index = Integer.parseInt(parts[1]);
					int lvIndex = -1;
					String name = parts[2];

					if (LEGACY) {
						lvIndex = index;
						index = -1;
					}

					String method = contextNamedStack.poll();
					if ("ARG".equals(parts[0])) {
						processor.acceptMethodArg(srcClsName, srcMethodName, srcMethodDesc, contextNamedStack.peek(), index, lvIndex, name);
					} else {
						processor.acceptMethodVar(srcClsName, srcMethodName, srcMethodDesc, contextNamedStack.peek(), index, lvIndex, name);
					}
					contextNamedStack.add(method);

					break;
				}

				case "FIELD":
					if (parts.length != 4) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String context = contextStack.peek();
					if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (field without class): " + line);
					processor.acceptField(context.substring(1), parts[1], parts[3], contextNamedStack.peek(), parts[2]);
					break;

				default:
					throw new IOException("Invalid enigma line (unknown type): " + line);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}