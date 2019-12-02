package com.chocohead.loom.util;

public interface MappingProcessor {
	void acceptClass(String srcName, String dstName);

	void acceptClassComment(String name, String comment);

	void acceptMethod(String srcClassName, String srcName, String desc, String dstClassName, String dstName);

	void acceptMethodComment(String className, String name, String desc, String comment);

	void acceptMethodArg(String className, String methodName, String methodDesc, String dstClassName, int argIndex, int lvtIndex, String argName);

	void acceptMethodArgComment(String className, String name, String desc, int argIndex, int lvtIndex, String comment);

	void acceptMethodVar(String className, String methodName, String methodDesc, String dstClassName, int varIndex, int lvtIndex, String varName);

	void acceptMethodVarComment(String className, String name, String desc, int argIndex, int lvtIndex, String comment);

	void acceptField(String srcClassName, String srcName, String desc, String dstClassName, String dstName);

	void acceptFieldComment(String className, String name, String desc, String comment);
}