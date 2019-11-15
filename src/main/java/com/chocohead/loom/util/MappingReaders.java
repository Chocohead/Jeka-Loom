package com.chocohead.loom.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.tinyremapper.TinyUtils;

public class MappingReaders {
	public static void readTinyV2From(Path file, MappingProcessor processor, String from, String to) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			TinyUtils.read(reader, from, to, processor::acceptClass, (field, name) -> {
				processor.acceptField(field.owner, field.name, field.desc, null, name);
			}, (method, name) -> {
				processor.acceptMethod(method.owner, method.name, method.desc, null, name);
			}, (method, locals) -> {
				for (int i = locals.length - 1; i >= 0; i--) {
					if (locals[i] == null) continue;
					processor.acceptMethodArg(null, method.name, method.desc, method.owner, i, i, locals[i]);
				}
			});
		}
	}

	public static void readEnigmaFrom(Path dir, MappingProcessor processor) throws IOException {
		EnigmaReader.readFrom(dir, processor);
	}

	public static Map<String, String[]> readParamsFrom(Path file) throws IOException {
		Map<String, String[]> lines = new HashMap<>();

		try (BufferedReader reader = Files.newBufferedReader(file)) {
			for (String line = reader.readLine(), current = null; line != null; line = reader.readLine()) {
				if (current == null || line.charAt(0) != '\t') {
					current = line;
				} else {
					int split = line.indexOf(':'); //\tno: name
					int number = Integer.parseInt(line.substring(1, split));
					String name = line.substring(split + 2);

					String[] lineSet = lines.get(current);
					if (lineSet == null) {
						//The args are written backwards so the biggest index is first
						lines.put(current, lineSet = new String[number + 1]);
					}
					lineSet[number] = name;
				}
			}
		}

		return lines;
	}
}