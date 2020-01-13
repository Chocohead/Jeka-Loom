package com.chocohead.loom.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.stream.Stream;

import dev.jeka.core.api.file.JkPathTree;

class EnigmaReader {
	private static final String TO_ESCAPE = "\\\n\r\0\t";
	private static final String ESCAPED = "\\nr0t";
	static final boolean LEGACY = true;

	public static void readFrom(Path dir, MappingProcessor processor) throws IOException {
		try (Stream<Path> stream = JkPathTree.ofZip(dir).andMatching("**.mapping").stream(FileVisitOption.FOLLOW_LINKS)) {
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
			StringBuilder commentBuffer = new StringBuilder();

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				int newIndent = 0;
				while (newIndent < line.length() && line.charAt(newIndent) == '\t') newIndent++;
				int indentChange = newIndent - indent;

				if (indentChange != 0) {
					if (commentBuffer.length() > 0) {
						buildComment(commentBuffer, contextStack, processor);
						commentBuffer.setLength(0);
					}

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
				if (parts.length == 0) continue; //Line only consisting of tabs

				if (commentBuffer.length() > 0 && !"COMMENT".equals(parts[0])) {
					buildComment(commentBuffer, contextStack, processor);
					commentBuffer.setLength(0);
				}

				switch (parts[0]) {
				case "CLASS": {
					if (parts.length < 2 || parts.length > 3) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String obfName = parts[1];
					if (indent >= 1) {//Inner classes have certain inconsistencies...
						//System.out.println("Passed inner class: " + obfName + " (" + obfName.indexOf('/') + ", " + obfName.indexOf('$') + ')');
						if (obfName.indexOf('/') > 0) {//Some inner classes carry the named outer class, others the obf'd outer class
							int split = obfName.lastIndexOf('$');
							assert split > 2; //Should be at least a/b$c
							String context = contextStack.peek();
							if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (named inner class without outer class name): " + line);
							obfName = context.substring(1) + '$' + obfName.substring(split + 1);
						} else if (obfName.indexOf('$') < 1) {//Some inner classes don't carry any outer name at all
							assert obfName.indexOf('$') == -1 && obfName.indexOf('/') == -1;
							String context = contextStack.peek();
							if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (named inner class without outer class name): " + line);
							obfName = context.substring(1) + '$' + obfName;
						}
					}
					contextStack.add('C' + obfName);
					indent++;
					if (parts.length == 3) {
						String className;
						if (indent > 1) {//If we're an indent in, we're an inner class so want the outer classes's name
							String context = contextNamedStack.peek();
							if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (named inner class without outer class name): " + line);
							//Named inner classes shouldn't ever carry the outer class's package + name
							assert !parts[2].startsWith(context.substring(1)): "Pre-prefixed enigma class name: " + parts[2];
							className = context.substring(1) + '$' + parts[2];
						} else {
							className = parts[2];
						}
						contextNamedStack.add('C' + className);
						processor.acceptClass(obfName, className);
					} else {
						contextNamedStack.add('C' + obfName); //No name, but we still need something to avoid underflowing
					}
					break;
				}

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
					if (parts.length < 2 || parts.length > 3) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String methodContext = contextStack.poll();
					if (methodContext == null || methodContext.charAt(0) != 'M') throw new IOException("Invalid enigma line (arg without method): " + line);
					String classContext = contextStack.peek();
					if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing method owner context from stack");
					contextStack.add(methodContext);

					int index = Integer.parseInt(parts[1]);
					boolean isArg = "ARG".equals(parts[0]);

					if (parts.length == 3) {
						int methodDescStart = methodContext.indexOf('(');
						assert methodDescStart != -1;

						String srcClsName = classContext.substring(1);
						String srcMethodName = methodContext.substring(1, methodDescStart);
						String srcMethodDesc = methodContext.substring(methodDescStart);
						String name = parts[2];

						String method = contextNamedStack.poll();
						if (isArg) {
							processor.acceptMethodArg(srcClsName, srcMethodName, srcMethodDesc, contextNamedStack.peek(), index, name);
						} else {
							int lvIndex;
							if (LEGACY) {
								lvIndex = index;
								index = -1;
							} else {
								lvIndex = -1;
							}
							processor.acceptMethodVar(srcClsName, srcMethodName, srcMethodDesc, contextNamedStack.peek(), index, lvIndex, name);
						}
						contextNamedStack.add(method);

						contextNamedStack.add((isArg ? 'A' : 'V') + name);
					} else {
						contextNamedStack.add(isArg ? "A" : "V"); //No name, but we still need something to avoid underflowing
					}

					indent++;
					contextStack.add((isArg ? "A" : "V") + index);
					break;
				}

				case "FIELD": {
					if (parts.length < 3 || parts.length > 4) throw new IOException("Invalid enigma line (missing/extra columns): " + line);
					String context = contextStack.peek();
					if (context == null || context.charAt(0) != 'C') throw new IOException("Invalid enigma line (field without class): " + line);

					assert parts[1].indexOf('#') < 0;
					assert parts[parts.length - 1].indexOf('#') < 0;
					contextStack.add('F' + parts[1] + '#' + parts[parts.length - 1]);

					indent++;
					if (parts.length == 4) {
						processor.acceptField(context.substring(1), parts[1], parts[3], contextNamedStack.peek(), parts[2]);
						contextNamedStack.add('F' + parts[2]);
					} else {
						contextNamedStack.add('F' + parts[1]); //No name, but we still need something to avoid underflowing
					}
					break;
				}

				case "COMMENT":
					if (contextStack.isEmpty()) throw new IOException("Invalid enigma line (comment without class/member): " + line);
					if (commentBuffer.length() > 0) commentBuffer.append('\n');

					if (parts.length > 1) {
						parseComment(line, "COMMENT".length() + 1, commentBuffer);
					}
					break;

				default:
					throw new IOException("Invalid enigma line (unknown type): " + line);
				}
			}

			if (commentBuffer.length() > 0) {//Remember to write what ever is left in the comment buffer
				buildComment(commentBuffer, contextStack, processor);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void parseComment(String line, int offset, StringBuilder out) throws IOException {
		int end = line.length();

		int pos;
		while ((pos = line.indexOf('\\', offset)) >= 0) {
			if (pos > offset) out.append(line, offset, pos - 1);
			if (++pos == end) throw new IOException("Invalid escape sequence: \\<eol>");

			char letter = line.charAt(pos);
			int index = ESCAPED.indexOf(letter);
			if (index < 0) throw new IOException("Invalid escape sequence: \\" + letter);
			out.append(TO_ESCAPE.charAt(index));

			offset = pos + 1;
		}

		if (offset < end) out.append(line, offset, end);
	}

	private static void buildComment(CharSequence comment, Queue<String> contextStack, MappingProcessor processor) {
		assert !contextStack.isEmpty(): "Tried to build comment with no context?";
		String text = comment.toString();

		switch (contextStack.peek().charAt(0)) {
		case 'C': {
			String name = contextStack.peek();
			processor.acceptClassComment(name, text);
			break;
		}

		case 'M': {
			String methodContext = contextStack.poll();
			assert methodContext != null; //poll should work just as well as peek

			String classContext = contextStack.peek();
			if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing method owner context from stack");

			int methodDescStart = methodContext.indexOf('(');
			assert methodDescStart > 1;

			String className = classContext.substring(1);
			String methodName = methodContext.substring(1, methodDescStart);
			String methodDesc = methodContext.substring(methodDescStart);

			processor.acceptFieldComment(className, methodName, methodDesc, text);
			contextStack.add(methodContext); //Put this back again
			break;
		}

		case 'A': {
			String argContext = contextStack.poll();
			assert argContext != null; //poll should work just as well as peek

			String methodContext = contextStack.poll();
			if (methodContext == null || methodContext.charAt(0) != 'M') throw new IllegalStateException("Missing argument owner context from stack");

			String classContext = contextStack.peek();
			if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing method owner context from stack");

			int methodDescStart = methodContext.indexOf('(');
			assert methodDescStart > 1;

			String className = classContext.substring(1);
			String methodName = methodContext.substring(1, methodDescStart);
			String methodDesc = methodContext.substring(methodDescStart);
			int lvIndex = Integer.parseInt(argContext.substring(1));

			processor.acceptMethodArgComment(className, methodName, methodDesc, lvIndex, text);
			contextStack.add(methodContext); //Put this back again
			contextStack.add(argContext);
			break;
		}

		case 'V': {
			String varContext = contextStack.poll();
			assert varContext != null; //poll should work just as well as peek

			String methodContext = contextStack.poll();
			if (methodContext == null || methodContext.charAt(0) != 'M') throw new IllegalStateException("Missing variable owner context from stack");

			String classContext = contextStack.peek();
			if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing method owner context from stack");

			int methodDescStart = methodContext.indexOf('(');
			assert methodDescStart > 1;

			String className = classContext.substring(1);
			String methodName = methodContext.substring(1, methodDescStart);
			String methodDesc = methodContext.substring(methodDescStart);
			int lvIndex = Integer.parseInt(varContext.substring(1));

			processor.acceptMethodVarComment(className, methodName, methodDesc, LEGACY ? -1 : lvIndex, LEGACY ? lvIndex : -1, text);
			contextStack.add(methodContext); //Put this back again
			contextStack.add(varContext);
			break;
		}

		case 'F': {
			String fieldContext = contextStack.poll();
			assert fieldContext != null; //poll should work just as well as peek

			String classContext = contextStack.peek();
			if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException("Missing field owner context from stack");

			int fieldDescStart = fieldContext.indexOf('#');
			assert fieldDescStart > 1;

			String className = classContext.substring(1);
			String fieldName = fieldContext.substring(1, fieldDescStart);
			String fieldDesc = fieldContext.substring(fieldDescStart + 1);

			processor.acceptFieldComment(className, fieldName, fieldDesc, text);
			contextStack.add(fieldContext); //Put this back again
			break;
		}

		default:
			throw new IllegalStateException("Unexpected (non-empty) context stack: " + contextStack);
		}
	}
}